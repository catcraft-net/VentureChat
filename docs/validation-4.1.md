# VentureChat 4.1 validation checkpoint

Status: implementation saved for later continuation at the owner's request on 2026-09-21. Draft PR; no merge or live deployment.

## Completed evidence

- Java 25 Maven `verify`: **97 tests, 0 failures, 0 errors, 0 skipped**, including opt-in exact ChatSentry tests and the 50,000-player storage benchmark.
- Command: `mvn -Dmaven.repo.local=../m2 -Dventurechat.benchmark=true -Dchatsentry.test.jar=/absolute/path/to/2-ChatSentry-5.6.7.jar verify` with Java 25. Proprietary JARs are not committed.
- ChatSentry input SHA-256: `67a5a5766360255c8c563d688d5ce5b2bac90387ff4745cff796e15582cce92b`.
- Packaged JAR SHA-256: `665e3fca2c0101a394c0b494b292224b4ccda11d1ae5fd9f0f67411fc0180e26`. Build timestamps may change archive hashes on rebuild.
- All packaged `.class` entries match the artifact used by the disposable Paper probe; subsequent edits only restored original line endings and updated documentation.
- `git diff --check` clean before saving.
- Independent review findings were fixed: PM-toggle permission, disabled-command namespace, bounded offline-name lookup, retention catch-up, actual resolved command ownership. Final readiness and shutdown-generation review found no remaining material issue.

## Disposable Paper fixture

Paper `26.2-124-ver/26.2@22ca6c7`, Java 25, ProtocolLib `5.5.0-SNAPSHOT-f0e959a`, Vault 1.7.3, LuckPerms 5.5.85, PlaceholderAPI 2.12.3, exact supplied ChatSentry 5.6.7.

Passed checks:

- Plugin and authenticated loopback dashboard startup; both scope and detector report AVAILABLE.
- Detector waits for actual ChatSentry initialization; configured `badword` matches, clean text does not. No empty startup snapshot.
- Real Bukkit inventory/item metadata creation; scheduled filter and PM-toggle clicks; click cancellation; offline ignored-player entry and removal by UUID.
- Modern SYSTEM_CHAT packet construction.
- Public message stored in new history; private message excluded. SQLite contained only the intended Global fixture row.
- SQLite preference row persisted `personal_filter=0`, `message_toggle=0`, and `ignores_json=[]` after shutdown.
- Plugin disable left no live history/dashboard worker threads. Fixture server stopped cleanly.

The probe used interface proxies for Player/InventoryView while using the real Paper registry, inventories, scheduler, plugin services and database. It is not a connected Minecraft-client or live-player test. Probe source is preserved at `docs/fixtures/paper/Probe.java`; compile against the disposable server libraries, VentureChat artifact and ProtocolLib, package as a fixture plugin depending on VentureChat and ChatSentry, and run only in an isolated test server. The probe deliberately shuts that server down.

Separate tests exercise actual SQLite lock failures/recovery, overflow, retention backlog catch-up, concurrent bursts, restart persistence, authenticated HTTP, and HTML-like content. A disposable Brave check verified dashboard login, clean columns, literal-text search, surrounding context, and lock. The listener and browser tab were closed.

## Limits and remaining validation

- No live realm configuration, real player session, item-sharing plugin, or deployed Parties version was tested.
- First release has one dashboard per realm, not remote aggregation.
- Parties-owned delivery has no per-recipient hook; native VentureChat parties/channels/DMs are covered. Do not claim Parties personal-filter coverage.
- Existing commandspy, legacy logs, and other plugins retain separate privacy policies; the new feature suppresses VentureChat PM-spy delivery for hidden PMs.
- Bounded best-effort logging can drop records on overload/crash/shutdown deadline; diagnostics expose gaps. Finite tests do not establish zero lag or zero leaks under every production condition.
- Maven's ProtocolLib repository returned HTML instead of a JAR during setup. The task cache was repaired using an existing valid 5.4.0 compile artifact. Check archive integrity in a fresh environment; production smoke used the actual 5.5.0 snapshot above.
