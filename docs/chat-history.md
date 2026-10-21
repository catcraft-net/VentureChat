# Chat history and private dashboard

History lives in `chat-history.db`, separate from player preferences. The server copies immutable message fields into a bounded queue. One daemon writer commits batches in the background; it never receives a Bukkit Player reference. `record` performs validation, allowlist checks and a queue offer, with no database/network work.

## Integration

```java
var settings = new ChatHistoryService.Settings(
    4096, 100, 30, 2000, Set.of("Global"), false);
var history = new ChatHistoryService(dataFolder.resolve("chat-history.db"), settings);
history.start();
// Only after the message has passed existing enforcement and delivery policy:
history.record(new ChatHistoryService.Event(
    UUID.randomUUID(), System.currentTimeMillis(), "Blue", channelName,
    senderUuid, senderName, null, plainMessage, false));
var dashboard = new HistoryDashboard(history,
    new InetSocketAddress("127.0.0.1", 8765), configuredSecret);
dashboard.start();
// Disable in this order:
dashboard.close();
history.close();
```

Create one event ID per message; reuse it on any retry or forwarding. The database has a unique event ID and uses `ON CONFLICT(id) DO NOTHING`, including retries after ambiguous commit outcomes. Sender UUID and name are preserved separately. `record` returning false means disabled/not running, excluded by policy, or rejected because the queue is full. Queue overflow and undrained shutdown entries contribute to dropped metrics; deliberately excluded content is not a gap.

The shipped `chat-history` config enables public history with realm `server`, channel allowlist `[Global]`, private logging false, 30 days retention, 4096 queue slots, batch size 100, and 2000 ms shutdown drain. Set a distinct realm name in each installation. The dashboard independently defaults disabled; set `chat-history.dashboard.enabled: true` and restart. It always binds `127.0.0.1`, uses configurable port 8765, and generates a 32-byte random credential in `dashboard-token.txt` (owner-only permissions where supported). History failures/drops produce a rate-limited console warning even when the dashboard is disabled.

Non-Global channels require an explicit allowlist entry. Private events or any event carrying a recipient UUID additionally require `logPrivate=true`; enabling private logging never implicitly expands the channel allowlist. No arbitrary commands or rejected sends belong in this database. The caller must label private group/party channels correctly. Native party forwarding and cross-realm transport are separate integrations.

## Browser access

Visit the loopback listener and paste the configured token. The browser holds it only in memory and sends an `Authorization: Bearer ...` header. It is never embedded in packaged assets, saved to browser storage, put in query parameters, or logged by these classes. Reloading or locking clears the token. There are no cookies, unauthenticated data endpoints, cross-origin grants, or state-changing HTTP APIs.

For remote staff access, use a TLS reverse proxy with your existing access control and rate/request/response timeout limits in front of the loopback listener. Direct public exposure and hosting deployment are outside this implementation. Four HTTP workers and sixteen queued tasks bound application concurrency; slow clients can occupy that bounded capacity, so the proxy must enforce connection limits and deadlines. This is a shared read-only staff token, not individual accounts or an audit trail; rotate it by replacing `dashboard-token.txt` with a new 32-byte URL-safe random token and restarting.

The interface filters by exact realm/channel, exact player name (case insensitive) or sender/recipient UUID, literal text, and local-browser time range. Pages contain at most 100 records (UI uses 50); offsets stop at 10,000. Narrow the date range to browse further. Paging is live, so arrivals can shift offset pages. Context reads the same realm/channel and, for private events, only the same sender/recipient pair. Content is rendered with `textContent`, never HTML. Restrictive CSP, no-store, nosniff, and no-referrer headers apply to every response.

API endpoints are `GET /api/messages`, `/api/context?id=<uuid>&radius=20`, and `/api/status`, all requiring the bearer header. Search parameters: `realm`, `channel`, `player`, `text`, `from`/`to` (epoch milliseconds), `limit` (1–100), and `offset` (0–10000). Context radius is 1–50. Invalid parameters return 400, missing/wrong authentication 401, and SQLite failures 503. SQL values use prepared statements; `%`, `_` and backslash in text are escaped literally. SQLite progress callbacks stop read execution after two seconds; lock waits are capped at 100 ms.

## Bounds, gaps and recovery

Maximum in-memory event count is queue capacity plus batch size. Every field is bounded (message 8192 UTF-16 code units; realm/channel/name 64); oversize or malformed event fields throw `IllegalArgumentException` before retention. Integration should catch malformed upstream messages without disrupting delivery. No disk spool is used: a process crash can lose all uncommitted queued events. Database `NORMAL` synchronization also does not promise survival of the most recent commits across power loss.

A storage failure retains the current batch and retries with backoff (100 ms to 3.2 seconds), keeping the queue bounded. Retry attempts continue while running so a restored disk can recover automatically; there is no unbounded retry list. New messages are dropped once the queue is full. The service exposes accepted, persisted, dropped, failure count, queued count, running state and a sanitized current SQLite error code. `persisted` counts acknowledged queue events, including duplicate IDs that already existed; it is not the number of unique database rows. Metrics reset on restart; they are operational indicators, not a durable gap ledger.

Shutdown stops intake, waits at most the configured drain deadline, then interrupts the daemon writer. Pending entries are counted as dropped when the writer exits; an active native filesystem operation may finish after the deadline. No claim is made that a blocked operating-system disk call can be forcibly stopped by Java. The writer opens scoped connections rather than retaining a connection after exit. Startup failures propagate so integration can visibly disable history.

Retention checks once per minute when caught up, independently of new traffic. Each pass deletes at most 1000 expired rows; a full batch schedules another pass after 250 ms until the backlog drains. The worker checks and writes its message batch between maintenance passes, so catch-up never becomes an unbounded delete loop. Deleted SQLite pages are reused; the file does not automatically shrink. Retention is a time policy, not a hard disk-byte cap. Back up or compact only while the service is stopped using normal SQLite operational procedures. Database backups contain any explicitly enabled private history and need the same access restrictions.

This version stores and filters realm IDs but does not aggregate remote realms. Run one private dashboard per realm, or implement an explicitly configured authenticated read-peer layer later; never place the SQLite file on a shared network filesystem.

## Validation

Focused disposable tests cover default private/channel exclusions, literal SQL searches, stable-ID deduplication, same-conversation context, malformed fields/searches, bounded overflow under a locked database, deadline shutdown, write failure/recovery, concurrent bursts, restart persistence, 1000-row incremental retention, authenticated HTTP and safe text rendering. These exercise actual SQLite and loopback HTTP. They do not substitute for sustained production-volume soak testing, real disk-full/power-loss testing, remote-proxy hardening, or live Paper delivery verification.

A disposable Brave browser smoke check verified login, rendered columns and layout, literal `100%` search, surrounding-context navigation, and lock. An HTML event-handler payload appeared as literal message text without execution. The fixture listener and tab were closed afterward.
