# Resume this change

Paused at the owner's explicit request, 2026-09-21. Preserve this branch and draft PR. Do not merge or deploy automatically.

Branch: `feat/player-chat-controls-and-history`; base: `master` at `efd818e3b3fb7c5b8a140fa2dedec1e4909294e4`.

## Already implemented

Player settings menu, persistent per-recipient filtering, offline unignore, safe explicit PM reply links, exact ChatSentry scope adapter, bounded SQLite history and authenticated dashboard, obsolete integration/compatibility removal. See `venturechat-roadmap.md` for decisions and defaults. No mentions or duplicate item-sharing feature.

Latest full build passed 97/97 tests, including exact proprietary-JAR fixtures and the optional storage benchmark. Disposable Paper checks and independent review passed. See `validation-4.1.md` for precise evidence and limitations. Do not repeat the initial investigation.

## Next steps

1. Read the draft PR and any review feedback; inspect current branch state before changing anything.
2. Finish the delivery/rollout documentation review. Confirm defaults and coverage are presented consistently across roadmap, filtering and history docs.
3. If desired, run the saved disposable Paper probe with the exact final rebuilt archive and verify persisted preferences on a second startup. The last tested classes match the saved artifact; the final archive differed only in packaging/resource line endings.
4. Validate actual realm configuration and a real client session before deployment: private channel names/range, aliases, item sharing, Parties if used, and dashboard access. Remove old CatChatScope before enabling the replacement; do not hot-reload.
5. Mark the PR ready when this remaining review is complete and report the JAR/PR. Merge/live deployment remains a separate action.

## Local continuity

The task workspace retains `work/venturechat-upgrade` (this checkout), `work/paper-chat-upgrade-smoke` (stopped disposable server and logs), `work/runtime-probe` (probe classes/source), and `work/m2` (repaired Maven cache). The supplied ChatSentry and CatChatScope JARs remain external attachments and are not in Git. Latest verification console was `/tmp/venturechat-release-verify.log`; durable test/validation evidence is summarized here.

All worker agents are stopped. No scheduled continuation was created; resume this draft when convenient.
