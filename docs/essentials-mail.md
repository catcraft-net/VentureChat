# VentureChat 4.1.2: EssentialsX mail ignores

When EssentialsX is installed, VentureChat automatically registers its public `UserMailEvent` listener. No command, permission or config change is needed. Startup reports `EssentialsX mail now respects VentureChat ignores.` Essentials remains an optional soft dependency; its code is not bundled.

A recipient ignoring a sender through VentureChat cannot receive new EssentialsX mail from that sender. UUIDs are used, including for offline recipients. Unignoring permits future mail. Existing mailbox contents remain unchanged. Console/legacy mail with no sender UUID remains unchanged. Staff PM-bypass permissions do not override mail ignores.

The check is at Essentials' delivery event, covering normal/temporary mail and player bulk sends through its mail service, independent of command aliases. It does not synchronize or overwrite Essentials' own ignore list. Essentials' usual sender acknowledgement remains, so it does not disclose an ignore. Mail is not logged by this bridge.

## Threading and failure behavior

Cached recipient state is checked on the server thread without disk access. Uncached recipients use VentureChat's existing background storage lookup, including queued persistence. A synchronous mail event is cancelled before a mailbox write; allowed mail is resubmitted through the public service on the main thread after lookup. A one-delivery permit prevents recursion, with current cached state checked again. Other plugins can still cancel the new event. Sender UUID/name, content and absolute expiry are preserved; the service assigns the final delivery timestamp.

Essentials' asynchronous bulk path waits at most five seconds off the server thread and does not replay its event. There are at most 128 pending checks. Timeouts keep their capacity until the actual work completes. Storage failure, timeout or capacity exhaustion withholds affected mail and emits a rate-limited staff warning; it does not bypass an unknown ignore list. Deferred sync mail is not replayed after five seconds or after expiry. Normal sender acknowledgement is not proof of delivery during these failures. There is no persistent retry queue.

Disable unregisters the listener, completes pending decision futures and prevents queued replays. Full restart is required for plugin replacement; hot reload is not supported. Unsupported Essentials APIs emit an integration-unavailable warning instead of preventing VentureChat startup. Direct third-party mailbox mutation outside Essentials' mail service is outside this hook.

## Focused validation

- Three lookup tests: recipient-to-sender direction, unignore, offline lookup and cached-state recheck, storage failure.
- Six exact-JAR service tests: real Essentials `MailServiceImpl`/`UserMailEvent` with in-memory test mailboxes; cached ignore/unignore/console, deferred delivery exactly once and expiry preservation, stored ignore/outage, shutdown and another plugin's cancellation, async bulk path, 128-check bound and capacity recovery.
- Two bundled configuration tests passed. Packaging succeeded. No unrelated benchmark or full-suite rerun.
- Independent read-only review found no material issue.
- Disposable Paper 26.2 build 124 / Java 25: both plugins enabled and listener installed; actual Essentials offline-user YAML mailboxes verified cached ignore, unignore, stored offline ignore, uncached allowed delivery once and listener removal on disable. Probe source is `docs/fixtures/paper/MailProbe.java`; it creates synthetic users only and shuts the server down.
- Initial runtime fixture used never-created Essentials users and failed its setup; creating the synthetic user records corrected it. No production fix was needed. Essentials printed its existing unsupported-server-version warning but enabled and passed these mail checks; this does not establish full Essentials compatibility on Paper 26.2.

Tests used local `EssentialsX-2.22.0-CatCraft-hidden-slot-fix.jar`, SHA-256 `0aaa26201576051db4447aed4505fced7debc9f8d7e6f29e1828380a116a3cc7`. This is the existing inventory-fix artifact, not a newly changed Essentials JAR. It is not committed or bundled. Run the exact fixture with `-Dessentials.test.jar=/absolute/path/to/EssentialsX.jar`.

Delivered `VentureChat-4.1.2.jar` SHA-256: `078abed8b46615e5ba444e8b4377cf6aa97a5694196e94b8bf76dd3638064816`. Archive CRC/descriptor checks passed and bytes match the successful Paper runtime artifact. No live realm deployment or merge was performed.
