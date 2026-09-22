# Delivery and rollout checkpoint

Implementation and documentation review are complete. PR #5 is prepared for review; no merge or live deployment is authorized by this checkpoint.

Branch: `feat/player-chat-controls-and-history`; base: `master` at `efd818e3b3fb7c5b8a140fa2dedec1e4909294e4`.

Version 4.1.1 adds whole-word personal censoring and polished settings/skin profiles. See `validation-4.1.1.md` for the focused patch checks.

The prior 4.1.0 full build passed 97 tests, including exact ChatSentry fixtures and the storage benchmark. Disposable Paper checks and independent review passed. For the earlier documentation resume, only two focused bundled-configuration checks were run for the resource/documentation tidy-up. No production Java changed in that earlier documentation-only resume. See `validation-4.1.md` for precise evidence and boundaries.

## Configuration before a test-realm rollout

1. Use Paper 26.2 / Java 25 and retain Vault, PlaceholderAPI and compatible ProtocolLib. The supplied ChatSentry 5.6.7 artifact is the supported adapter build.
2. Stop the server, preserve the existing VentureChat configuration and data, replace VentureChat, and remove the old CatChatScope JAR. Use a full restart, not hot reload.
3. Copy the new configuration sections from the bundled example into the existing config as needed. Existing config files are not automatically overwritten. Verify `chatsentry-scope.private-channels` matches the actual private channels (defaults: Local and Group); Global must remain publicly moderated. No chat radius is changed. Review resolved private-command aliases.
4. If obsolete Town/Nation/Faction routes and their old enable flags remain, rename/delete those routes and configure their intended access before starting. The plugin refuses to silently expose them as ordinary channels.
5. Set `chat-history.realm`. New history defaults to Global only and private logging off. Enable the dashboard explicitly if wanted, restart, and use `dashboard-token.txt` through loopback or a secured tunnel/access proxy. Keep that credential private.
6. Confirm both ChatSentry adapters report AVAILABLE. Exercise `/chatsettings`, PM toggle, offline unignore, filter-on/filter-off recipients, public enforcement, and the existing item-sharing plugin with real clients before wider rollout.

Parties-owned delivery has no per-recipient filter hook; do not treat its messages as personally filtered. One dashboard runs per realm. These are documented boundaries, not unfinished native VentureChat features.

## Local continuity

The task workspace retains `work/venturechat-upgrade` (checkout and built JAR), `work/paper-chat-upgrade-smoke` (stopped disposable server/logs), `work/runtime-probe`, and `work/m2` (repaired Maven cache). Supplied proprietary JARs are external attachments, not committed. Probe source is saved under `docs/fixtures/paper` and deliberately shuts down its disposable server.

Future work should begin with current PR feedback and real-client skin/item-sharing checks. Repeat tests only for changed code or unresolved concerns; there is no need to repeat the initial investigation or benchmark for documentation edits.
