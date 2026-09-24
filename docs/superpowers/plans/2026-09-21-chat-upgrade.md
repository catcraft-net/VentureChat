# Player chat controls and searchable history implementation plan

Goal: implement the approved roadmap and exact-JAR review in an isolated branch, validate it, and publish a reviewable pull request. User approved implementation and recommendations on 2026-09-21; routine defaults and execution choices are delegated to us.

Spec: `docs/venturechat-roadmap.md` and `docs/catchatscope-review.md`, as clarified below.

## Design choices

- Java 25, Paper 26.2 baseline. Retain ProtocolLib, Vault and PlaceholderAPI. No mentions or duplicate item sharing.
- Persist a new `personalFilter` boolean independently of the old outgoing filter. Default true for new/legacy records; false must survive restart, fallback and recovery. Preserve compatibility constructors where reasonable.
- Menu command `/chatsettings` exposes personal filter, PM receive toggle, PM sounds, eligible channels and a paginated ignore list with offline removal. Server-owned inventory holder validates every click/drag; no inventory item movement. Offline name lookup must not block the tick thread.
- Sender echo remains normal for personally hidden PMs; no recipient sound, reply-state change, spy disclosure, or punishment from personal filtering. A sender's own channel message is not hidden. Existing ignore, mute, permission, visibility and proximity decisions precede delivery.
- ChatSentry 5.6.7 detector integration must verify exact supported signature/version and run its non-enforcing path once per message where practical, without synthetic chat events. Unsupported/unavailable detector is visible to staff/player and cannot silently claim effective filtering. Locally configured literal rules are an optional independent provider, not an implicit replacement for strict rules.
- Review CatChatScope replacement strategy against real ChatSentry cancellation behaviour; avoid copying proprietary implementation. Do not retain temporary player-wide permission races. Parties integration must use actual API state where possible and per-recipient delivery events if supported.
- History uses a separate SQLite database and bounded background writer; configurable retention, bounded searches, stable IDs and clear health/gap reporting. Default log allowlist is Global; PM/native party logging disabled. No arbitrary command logging.
- Dashboard serves packaged static assets with authenticated read APIs on loopback by default; token not in URL or logs. Bounded concurrency, escaped text output, search paging and context lookup. Remote realms require explicitly configured endpoints/access, not shared database files. No live hosting deployment implied.
- Approved cleanup removes Factions/MassiveCore/Towny and unreachable old Minecraft paths. Preserve public APIs used by external plugins when feasible.

## Tasks and ownership

1. **History and dashboard** — independent worker owns `logging/`, `resources/dashboard/`, their tests, and `docs/chat-history.md`. Public integration surface `ChatHistoryService` startup/shutdown and `record(...)` documented by worker. Parent wires main plugin and delivery points. Red tests for SQL search escaping, retention, queue bounds, retry deduplication, authenticated HTTP, output escaping, shutdown and failure metrics; then minimal implementation and green tests.
2. **Dependency/legacy cleanup** — independent worker owns pom, lib removal, Format/VersionHandler/PacketListenerLegacyChat, Chwho, VentureChatGui and executor legacy fallback. Parent owns ChatListener and MineverseChat; worker supplies exact integration guidance. Test modern formatting/packet generation and compile/linkage; remove old branches without replacing the current protocol mechanism.
3. **Personal filtering and ChatSentry scope** — independent worker owns `filter/`, optional integration-only classes and their tests. Parent wires saved player preference and delivery. Verify actual supplied detector without enforcement; test scoped public/private moderation and Parties hooks, unavailable-provider behaviour and concurrency. Expose simple immutable decision API for one message and per-recipient predicate. No private third-party JARs committed.
4. **Preferences, menus and delivery integration** — parent owns player state/repositories, settings GUI, PM/channel delivery, main lifecycle and shared config. Red tests for legacy migration, false preference roundtrip, offline unignore, inventory protection, PM privacy and eligible-recipient filtering; then implementation.
5. **Validation and review** — build/package full suite, exact JAR inspection, disposable Paper/ChatSentry smoke fixture, HTTP/browser verification, representative burst/failure/restart tests; independent final review and fixes. Record exact tested scope/limits, hash final artifact, update docs.
6. **PR** — commit source/tests/docs only, push feature branch, create PR against master with concrete behaviour and validation, attach PR to task. No merge or live rollout.

## Review focus

- Personal filtering never relaxes public enforcement or leaks an ignored/hidden PM through sound, reply state, spy, or UI.
- History outages, malformed searches and slow clients cannot create unbounded memory/tasks or stall gameplay delivery.
- Migration, restart and recovery preserve the new preference without reinterpreting old flags.
- Existing configuration and plugin APIs retain compatibility, including item-sharing components and channel routing.
- Exact proprietary detector tests prove no enforcement effects; unsupported versions are explicit.

## Progress

- [x] Read scope, inspect current remote baseline, create isolated branch.
- [x] Build dependency cache repaired; final full suite passed (standalone pre-change baseline was not completed).
- [x] History/dashboard.
- [x] Cleanup.
- [x] Filter/scope integration.
- [x] Preferences/menu/delivery.
- [x] Full suite, disposable Paper validation and independent review; live rollout checks remain separately documented.
- [x] Resumed documentation/configuration review and focused validation; PR and built artifact delivered for review. See docs/CONTINUE-HERE.md for rollout instructions.
