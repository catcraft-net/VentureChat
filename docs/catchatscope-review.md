# CatChatScope 1.0.0 and ChatSentry 5.6.7 inspection

Date: 2026-09-21
Method: archive inspection, CFR decompilation, and javap bytecode inspection of the exact user-supplied JARs. Originals were not modified. This section records the initial static inspection; later exact-JAR and disposable Paper validation is recorded in `validation-4.1.md`. No live configuration or installed-plugin inventory was supplied.

## Artifact identity

| Artifact | SHA-256 |
| --- | --- |
| CatChatScope-1.0.0.jar | `6c8ddc1cd91a7f990a61c67b7c08798b310003872b51b0a02edd2caaf87111ed` |
| ChatSentry-5.6.7.jar | `67a5a5766360255c8c563d688d5ce5b2bac90387ff4745cff796e15582cce92b` |

Original artifacts remain outside the repository. Decompiled output was an inspection aid, not original source or a rebuildable project, and is not bundled. Significant obfuscated boolean branches were checked against bytecode.

## What CatChatScope does

It temporarily grants the sender `chatsentry.wordandphrasefilter.bypass` while the relevant Bukkit chat or command event is processed, then removes its own permission attachment at MONITOR priority. It does not classify words itself, alter individual recipients, provide a player toggle, or change proximity rules.

Its bundled configuration sets `global-channel: Global` and exempts:

- Every other recognized VentureChat channel, by channel name or alias.
- VentureChat PM commands: `message`, `vmessage`, `msg`, `tell`, `whisper`, `pm`, `reply`, `vreply`, `r`.
- Persistent VentureChat private conversations and native party-chat mode.
- The Parties `/p` message command and direct-chat state inferred from `party chat` / `clan chat` commands.

VentureChat quick-channel messages are resolved using the quick channel rather than the normal channel, after the Parties tracked-state check. Unknown VentureChat state does not request a bypass. Existing permanent permissions are left alone.

The policy is literally Global versus non-Global, not a verified list of private channels. A public Trade/Help channel would also be exempt if configured under another name. The live channel list must be inspected before replacing this policy with an explicit allowlist.

The bundled Parties command list includes `/p`, not `/c`. `/c` may still be covered if it is a recognized VentureChat channel alias or the live CatChatScope configuration adds it. These JARs alone do not establish the actual command mappings on CatCraft.

## What remains active

Only the Word and Phrase Filter permission is granted by default. ChatSentry's spam, advertising/link, caps, and other checks can still apply according to their live configuration and permissions. The supplied ChatSentry chat/command listeners check those module permissions separately.

VentureChat's own filter is also independent. CatChatScope logs a startup warning for a non-Global VentureChat channel with `filter: true`; it does not turn that filter off. A personal incoming filter cannot restore text already replaced or blocked by another outgoing filter. PM/conversation filtering paths also need inspection of the deployed VentureChat build.

The range and membership requirements for the user's proximity group are owned by the chat/channel plugin, not CatChatScope. No 200-block setting is present in this JAR.

## Event ordering and lifecycle

- CatChatScope declares a hard dependency on VentureChat, a soft dependency on Parties, and `loadbefore: [ChatSentry]`.
- It opens bypasses at LOWEST and closes them at MONITOR for `AsyncPlayerChatEvent` and `PlayerCommandPreprocessEvent`.
- ChatSentry 5.6.7 handles ordinary chat at LOW and commands at LOWEST, making same-priority command registration order relevant.
- CatChatScope checks that its first handler precedes ChatSentry's first handler after startup and disables itself if the check fails or ChatSentry is absent. It recommends a full restart rather than `/reload`.
- It reference-counts overlapping private events for each player and removes tracked attachments on shutdown. The Parties UUID tracker removes players on quit/kick.

These cleanup paths are positive design features, not proof of zero leaks or concurrency safety under all conditions.

## Concerns to resolve before extending it

1. **Player-wide temporary permission can affect overlapping events.** A public chat/command check occurring while the same sender has a private-event bypass can observe that permission. Event-indexed bookkeeping does not make the underlying Bukkit permission event-local. There is also a close/open window between removal from the maps and removal of the attachment. These are static concurrency concerns, not observed production incidents.
2. **Parties state is inferred rather than read as authoritative state.** It schedules a local toggle after an uncancelled command and checks permission/membership, but does not verify that Parties accepted the requested mode. Its resolver then trusts that UUID set until another tracked toggle or quit/kick. Rejected syntax, untracked aliases, leaving a party, or mode changes through another path need coverage. The existing local Parties checkout also rejects invalid on/off arguments; it is supporting evidence, not a verified deployed version.
3. **The adapter is tied to legacy Bukkit chat events.** A future VentureChat move to Paper `AsyncChatEvent` needs coordinated integration work, not only replacing the listener type.
4. **Per-recipient filtering must integrate with the plugin that delivers each message.** A VentureChat-only change does not automatically cover independent Parties/clan delivery.

## Personal-filter feasibility in the supplied ChatSentry build

The public `com.kixmc.csapi.api.API` exposes only `getPluginVersion()`. The named InternalAPI class does not expose a content-check method either.

However, the obfuscated filter implementation `mXcL4z` has a method with this bytecode signature:

`String a(Player, String, boolean, xuNkC8)`

The ordinary chat listener passes `true`. The implementation also invokes this method internally with `false` and a null player. All five module-trigger construction branches in this method are gated by the boolean; passing false skips those associated event, player-message, warning, notification, and detection-log blocks in the inspected method. The bytecode branch offsets are recorded in `analysis/chatscope-review/chatsentry-filter-bytecode.txt`.

This is stronger evidence than the public documentation alone: a non-enforcing evaluation path appears to exist in 5.6.7. It is not a supported API or a completed integration. It still uses configuration, normalization, optional online-name checks, censor helpers, and other internal utilities; it is not proven pure or thread-safe. Invalid regex rules can still produce diagnostic logging. Runtime behaviour, return semantics, performance, reload races, and absence of indirect side effects require verification.

Recommended next evaluation: a disposable, exact-version adapter test using both a clean message and representative matching/near-matching messages from the real configuration. Verify no warning increments, moderation events, notifications, sender messages, punishment commands, or detection-log records. Verify result handling with censoring enabled and disabled. Do not submit synthetic private messages through Bukkit's normal chat event to obtain a verdict.

If this integration is selected, detect unsupported ChatSentry versions explicitly and do not silently claim that filtering remains operational after an update. An independently implemented personal filter remains an alternative with less coupling but different detection behaviour. Do not copy the proprietary detector implementation into VentureChat.

## Recommendation

Preserve the existing public/private word-filter distinction while implementing recipient-specific hiding at delivery. Evaluate the exact-version ChatSentry adapter before choosing between reusing its detector and a separate rule engine. Harden the temporary-permission and Parties-state handling as part of a reviewed integration design; no replacement/removal of CatChatScope is yet approved.

Still needed for implementation: live CatChatScope config, ChatSentry config and Word and Phrase Filter rules, VentureChat channels/commands and deployed version, Parties version/config, and any server-level aliases. Bundled JAR defaults are not a substitute for these.
