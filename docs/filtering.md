# Personal incoming filtering and moderation scope

The saved `personalFilter` preference applies after ordinary recipient eligibility checks. A sender's own echo is retained. The censor result is computed once and reused for recipients; matching text becomes asterisks for opted-in recipients, while opted-out eligible recipients retain the original message. The rest of the message remains readable. Sender mutes, ignores, channel membership, permissions, distance and existing moderation still apply. Personal filtering does not cancel delivery, sounds or reply state. Sender echoes retain their original content. Spy copies follow the receiving staff member's own personal-filter preference.

## Integration API

```java
var filter = new PersonalFilterService(
    ChatSentry567Detector.connect(chatSentryPlugin),
    config.getStringList("personal-filter.additional-literals"),
    plugin.getLogger()::warning);
CensorResult result = filter.censor(message);
String incoming = optedIn && !senderUuid.equals(recipientUuid)
    ? result.censored() : result.original();
```

`MATCH`, `CLEAN` and `UNAVAILABLE` are distinct. `UNAVAILABLE` keeps the original message readable. Provider failures appear in staff diagnostics, with a simple unavailable notice in settings; per-message localization limits are reported in the result without disabling the provider. Optional literal substring rules can independently match text, but a non-match never turns an unavailable strict provider into `CLEAN`. Literal rules use Java Unicode-aware case-insensitive region matching without changing text offsets; they do not implement ChatSentry's normalization, fuzzy matching or regex language. They are an additional explicit operator configuration, not a hidden substitute.

Connect on the main server thread **after ChatSentry finishes its deferred module initialization**. Poll `ChatSentry567Detector.isReady(plugin)` with a bounded startup retry; merely scheduling one tick after VentureChat enable is insufficient. Readiness checks the original detector's similarity helper, assigned after rule/configuration loading, rather than treating a potentially legitimate empty rule list as incomplete. Snapshot creation itself rejects an uninitialized detector. The adapter requires version 5.6.7, the expected main class and SHA-256 `67a5a5766360255c8c563d688d5ce5b2bac90387ff4745cff796e15582cce92b`. It selects the obfuscated getter by return type because multiple JVM methods share its name/arguments. It copies mutable detector rule lists/maps and serializes incoming evaluations. The copied detector calls the original method with a null Player and enforcement=false. Any non-null return is a match. The detached copy always has native censoring disabled, regardless of the live setting, so the adapter never enters ChatSentry's recursive native censor helper. The live global censor setting is unchanged.

`CensorResult` contains original text, censored text and the verdict. Its two strings have identical UTF-16 lengths; every changed character is `*`. The adapter finds matching word intervals with bounded whole-word probes of the same non-enforcing native detector. It masks complete whitespace-delimited words, retaining their outer punctuation and spacing; a contains/regex match inside a word masks that whole word. This avoids inventing an exact-word match by cutting into a clean word, or exposing the tail of a fuzzy match. Additional literal rules still mask only their exact substring. It does not implement a second regex/fuzzy rule engine. Clean messages need one native evaluation; localization allows at most 128 evaluations for messages up to 2,048 UTF-16 units. An oversized message, exhausted probe budget or invocation failure returns the original text with `UNAVAILABLE`; local additional literals can still be applied. These are deliberate fail-open limits, not a claim of complete filtering under every input. Context-sensitive regex and fuzzy rules can produce a wider matching interval than a literal word because the native API exposes only a verdict, not spans. Invalid or pathological operator regexes retain the original detector's behavior and are not given a regex timeout by this adapter.

The old `evaluate` and `shouldHide` methods remain for compatibility, but incoming delivery uses `censor` and must not drop a recipient on `MATCH`. Rich-message delivery projects changed text positions into message-body text components and leaves formatting, hover and click metadata intact. Native VentureChat components retain their prefix; custom component layouts require an identifiable prefix/body boundary, otherwise their original delivery is retained.

Rules are an enable-time snapshot: perform a full restart after changing ChatSentry rules. The adapter retains ChatSentry's small shared utility/config references and normal online-name lookups where configured; it does not claim arbitrary third-party reload operations are thread-safe. A disable or invocation failure latches a visible unavailable state until reconnect/restart. No enforcement method, synthetic chat event, player permission attachment or warning counter is used for incoming filtering. Neither proprietary JAR nor decompiled detector code is bundled.

## Event-local replacement for CatChatScope

`ModuleTriggerEvent` in the exact build is not cancellable. `iPXyi` schedules its notification for a later tick, after enforcement has already run. Cancelling that notification cannot provide a safe bypass.

The integrated replacement wraps only the exact ChatSentry chat/command registrations. For a recognized private event it creates an event-local shallow copy of ChatSentry's module settings, changes only the copy's word-filter-enabled flag, and invokes the original listener class with the original event and Player. All other modules retain their real helper objects and state. Public/unknown events go through the original registered listener. No live module setting or permission is temporarily changed, and simultaneous private/public events cannot observe each other's copied flag. Errors do not replay a partially executed listener.

Wire after ChatSentry enable, on the main thread:

```java
var policy = new VentureChatScopePolicy(privateChannelNames, privateCommandLabels,
    PartiesModeReader.connect(partiesPlugin, plugin.getLogger()::warning));
var scope = ChatSentryScope.install(chatSentryPlugin, policy, plugin.getLogger()::warning);
// On VentureChat disable:
scope.close();
```

`scope.status()` reports whether installation succeeded. Installation requires both expected listener registrations. It refuses to run while CatChatScope is enabled. **Remove/disable the old CatChatScope JAR and perform a full restart before enabling the replacement**; merely leaving both loaded would retain the old player-wide permission race. A failed installation leaves original ChatSentry enforcement intact. Plugin hot reload is unsupported. The wrapper retains ChatSentry's priority; its registration moves to the end of that same priority, so verify any other same-priority integrations in the disposable-server test.

Recommended configuration keys are `chatsentry-scope.enabled`, `chatsentry-scope.private-channels`, and `chatsentry-scope.private-commands`. Use an explicit private-channel allowlist, such as the actual proximity/group channel. Trade, Help and any unknown channels remain moderated unless deliberately listed. VentureChat private conversations and native party mode are recognized; quick-channel messages resolve their actual quick channel. Private command labels must match the deployed mappings; resolved command-map entries must be the actual VentureChat Message/Reply command object or ChannelAlias object. Missing, disabled or remapped labels remain moderated. Namespaced Parties commands additionally require the configured complete label and verified enabled Parties plugin ownership; arbitrary foreign commands are never exempt. Do not assume `/p` or `/c` belongs to a particular plugin. Server command aliases require explicit review.

Example only, to be adapted to the live channel names and command mappings:

```yaml
chatsentry-scope:
  enabled: true
  private-channels: [Local]
  private-commands: [message, vmessage, msg, tell, whisper, pm, reply, vreply, r]
```

The original VentureChat outgoing filter is independent. `personal-filter.legacy-outgoing-private-filter` defaults false for the configured private channels, DMs and native parties; setting it true restores old sender-side censoring. Incoming filtering cannot restore words already censored or blocked upstream.

## Parties coverage boundary

The inspected Parties checkout exposes whole-message PreChat and PreBroadcast events. `PartyImpl.dispatchChatMessage` then directly sends each member's text and sound; there is no recipient event/recipient list that this plugin can safely edit. This integration therefore does **not** claim personal-filter coverage for Parties-owned delivery and does not cancel/recreate Parties delivery (which would change spies, sounds, Redis routing and formatting).

The optional mode reader reads `PartyPlayerImpl.isChatParty()` and a non-null party ID from the actual cached state. It uses no inferred command toggles and does not call the database-capable `getPartyPlayer` fallback. An incompatible Parties implementation produces a visible unavailable notice and remains subject to normal moderation. This is an internal compatibility adapter and needs verification against the deployed Parties version. For per-recipient filtering, Parties needs a companion delivery hook before both text and sound, carrying sender UUID, recipient UUID and raw message; remote/Redis delivery must use the same hook.

## Verification

`PersonalFilterServiceTest` covers explicit unavailability, additional local rules and recipient/self preferences. `VentureChatScopePolicyTest` covers explicit channel/command scope, quick public messages while in a private conversation, namespaces and unknown routes.

The two opt-in exact-JAR tests load the original artifact in an isolated classloader:

```sh
JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home \
  mvn -Dmaven.repo.local=../m2 \
  -Dtest=PersonalFilterServiceTest,VentureChatScopePolicyTest,ChatSentry567ExactJarTest,ChatSentryScopeExactJarTest \
  -Dchatsentry.test.jar=/absolute/path/to/2-ChatSentry-5.6.7.jar test
```

The detector fixture runs the original detector and similarity implementation with synthetic rule configuration: exact, contains, regex, phrase and near-match cases; live censoring enabled/disabled; readable same-length multiple-word, punctuation, whitespace, contains, regex, phrase, short-word and nocensor masks; unchanged clean/near-match text; 128-probe and 2,048-character bounds; detached rule-list mutation; 160 evaluations across four workers. Null enforcement helpers and an inert Main prove those paths are unused; Bukkit interactions are restricted to version metadata reads. No punishment commands, notifications, moderation events, sender messages or detector log writes occur in this fixture. This proves the exercised fixture paths, not every possible deployed rule or online-name configuration.

The scope fixture runs both actual listener classes with observed mock module helpers: 160 concurrent public/private events for the same Player, plus sequential controls. Every public word match remains cancelled, every private event bypasses only that module, the other enabled module runs for every message, and original flags never change. Player identity stays unchanged; no permissions are added or removed. The same exact-JAR fixture also verifies HandlerList installation, wrapper dispatch and restoration of both original registrations on close. This is disposable runtime evidence, not a live-server soak test. Full enable ordering, real rules, plugin lifecycle and deployed Parties compatibility still require the separate disposable Paper fixture and eventual live validation.
