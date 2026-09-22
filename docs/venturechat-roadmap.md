# CatCraft VentureChat roadmap and decision tracker

Updated: 2026-09-23. Implementation approved by the server owner; changes target Paper 26.2 / Java 25. No live rollout or merge is part of this change.

| ID | Area | Implementation / decision |
| --- | --- | --- |
| VC-01 | Chat settings | `/chatsettings`: persistent personal filter, PM receive toggle, sounds, eligible channel subscriptions. Title is Chat Settings, lore uses short player-facing lines, toggles show bold green ON/red OFF, and player icons carry skin profiles. PM toggle retains its existing permission. Required/current channels cannot be left through the menu. |
| VC-02 | Personal incoming filter | Default on. Censor matching words with asterisks per recipient, preserving other recipients and sender echo. Exact supplied ChatSentry 5.6.7 rules, initialized snapshot, no sender penalties. Separate from the old outgoing preference. |
| VC-03 | Ignore management | Paginated list; click to unignore by UUID, including offline players. Existing `/ignore <name>` adds players. Name and offline skin lookups are bounded and asynchronous; UUID remains usable if the old name is unavailable. |
| VC-04 | History | Separate SQLite database, bounded asynchronous writer, retry/health metrics, retention, indexed search. Global-only default; private logging off. |
| VC-05 | Dashboard | Authenticated loopback browser UI, literal-text/date/player/channel/realm search and context. Enable explicitly in config. First release runs one dashboard per realm; remote aggregation remains a future extension. |
| VC-06 | Factions/MassiveCore | Integration code, dependencies and bundled legacy JARs removed. |
| VC-07 | Towny | Town/Nation integration removed. Old enabled private routes block configuration loading until renamed/deleted and their intended access configured. |
| VC-08 | Legacy Minecraft | Obsolete internal version branches removed. Public VersionHandler compatibility queries retained for external callers. Modern SYSTEM_CHAT delivery remains on ProtocolLib. |
| VC-09 | Safer PM reply | Click a PM to suggest an explicit `/msg Name `; existing `/reply` remains. Clicking does not send. |
| VC-10 | Conversation reminder | Settings menu shows current private-conversation target with exit control. An unavailable/vanished target ends conversation without falling back to public delivery. |
| VC-11 | Mentions | Excluded; no `@name` feature. |
| VC-12 | Item sharing | Excluded; preserve existing formatted components for visible messages. |
| VC-13 | ProtocolLib/Vault/PlaceholderAPI | Retained required dependencies. |
| VC-14 | CatChatScope replacement | Exact-build event-local ChatSentry word-filter exemption; explicit private-channel allowlist, resolved-command validation, no player-wide permission mutation. Remove old CatChatScope JAR and restart before using replacement. |
| VC-15 | Parties | Actual cached chat-mode reader where compatible. Parties-owned delivery lacks per-recipient hooks; a companion Parties change is needed for personal filtering there. VentureChat channels/native parties/DMs are covered. |

## Defaults and deployment checkpoints

- Personal filtering defaults on for new and existing players; opt-out survives SQLite, legacy fallback and recovery. Rule changes require a full restart. Unsupported/disabled ChatSentry reports unavailable in menu and logs; a missing detector does not cancel every message.
- Global/public moderation remains ChatSentry enforcement. Personal opt-out never restores a globally blocked message. Only the word/phrase module is exempted for configured private routes; other moderation modules remain active.
- Private channel defaults are `Local` and `Group`. Review actual realm channel names and command mappings before rollout; no radius is changed. The legacy outgoing censor is disabled for these private routes/DMs/native parties by default so opted-out recipients can see unchanged content. `personal-filter.legacy-outgoing-private-filter` restores the older censor if desired.
- Ignored PMs produce no recipient message/sound/reply update or VentureChat PM-spy message. Personally filtered PMs are delivered with matching words masked and retain normal notifications/replies; spies follow their own filter preference. Existing commandspy, legacy logging and independent plugins have their own policy and are not retroactively controlled by this feature.
- New history defaults to `Global`, 30 days, queue 4096 plus batch 100, private logging false. Private channels require both an allowlist entry and explicit private logging. No arbitrary command logging is added.
- Set `chat-history.realm` per realm. Enable `chat-history.dashboard.enabled`, restart, and use `dashboard-token.txt` through loopback or a secured tunnel/access proxy. The shared token is generated automatically and never printed.
- This is bounded best-effort logging. Full queues or shutdown deadlines can drop records; health counters/log warnings expose that. Crash durability, unlimited disk stalls and zero production lag/leaks are not promises a finite test can establish.

## Evidence and documentation

- Baseline source: `efd818e3b3fb7c5b8a140fa2dedec1e4909294e4`.
- [Exact original JAR inspection](catchatscope-review.md) records original behavior and input hashes.
- [Personal filtering and scope](filtering.md) explains exact-version coupling and coverage.
- [Chat history and dashboard](chat-history.md) explains operating bounds and configuration.
- [Validation](validation-4.1.md) records tests, disposable Paper checks and remaining live rollout checks.

## Future work

- A safe per-recipient Parties text/sound hook, including remote delivery, if that plugin is used for private groups.
- Authenticated multi-realm read aggregation and individual staff accounts if one dashboard for all realms is required.
- Production soak with actual ChatSentry rules, configured aliases, item-sharing plugin and representative chat volume before rollout beyond a test realm.
