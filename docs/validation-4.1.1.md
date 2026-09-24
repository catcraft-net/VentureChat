# VentureChat 4.1.1 validation

This patch changes incoming personal filtering from hiding a message to censoring complete matching words. It also polishes Chat Settings and supplies player-head profiles. No live realm deployment or PR merge was performed.

## Focused checks

Java 25, local Maven cache, supplied exact ChatSentry 5.6.7 artifact (SHA-256 `67a5a5766360255c8c563d688d5ce5b2bac90387ff4745cff796e15582cce92b`).

- 23 targeted tests passed: `IgnoredPrivateMessageTest`, `CensoredChatTest`, `ChatSettingsMenuTest`, `PersonalFilterServiceTest`, `ChatSentry567ExactJarTest`. No failures/errors/skips. Coverage includes ignored-message silence, DM/reply/conversation/native-party/channel delivery, sender and opt-out originals, staff bypass, recipient sounds/reply state, colours, split components and unchanged item hover/click metadata.
- Independent review and the first real Paper probe exposed character-probe problems: synthetic exact matches inside clean words and partially visible fuzzy words. Replaced character trimming with complete-word localization. Added exact-JAR regressions for `example notbad examples` and `hello badword, friend`.
- After that fix, all five detector/service tests passed again and Maven packaging succeeded. Exact fixture exercised ten messages with live censoring on/off; typical two-word censoring used six native probes. The 128-probe and 2,048-character limits remain tested. No unrelated storage benchmark or full suite was repeated.
- GUI navigation regression was observed failing before its fix; five menu tests passed after it. A pending lookup transfers its existing permit when navigating pages, without overlapping skin requests.

Commands use `mvn -o -Dmaven.repo.local=../m2 -Dtest=<classes above> -Dchatsentry.test.jar=/absolute/path/to/original.jar test` and `package`. The existing cache still has a malformed ProtocolLib POM warning, but the compile JAR is valid and compilation/package succeeded.

## Disposable Paper check

Paper 26.2 build 124, Java 25, existing test dependencies including actual ChatSentry 5.6.7; loopback-only disposable server. The updated `docs/fixtures/paper/Probe.java` passed:

- Initialized scope/detector; `hello badword, friend` becomes `hello *******, friend`.
- Real menu items, player-facing lore, green bold ON and red bold OFF.
- Viewer UUID and supplied texture property preserved in skull metadata.
- Menu clicks, preference changes, offline unignore, SYSTEM_CHAT packet creation.
- Normal plugin shutdown with no history/dashboard worker left running; server exited cleanly.

The first launch was blocked by sandbox loopback binding; it was rerun with the required permission. The first actual runtime run caught the fuzzy-censor bug described above; the final run passed after the fix.

## Delivered artifact and boundaries

`VentureChat-4.1.1.jar` SHA-256: `49ed560dee1b2ba5f3ca84605438dde97688eaf0896e993ac768ac85a04cc844`.
Archive CRC check passed; the delivered bytes exactly match the plugin used in the successful final Paper run.

Offline skins depend on Paper/Mojang resolution; the fixture proves supplied profile data survives metadata creation, not actual Minecraft client texture rendering. Existing item-sharing metadata is covered by component tests, not a live third-party plugin session. Native contains/regex matches censor the complete matched word; context-sensitive rules may include wider word spans. Probe/length limits fail open; pathological operator regexes retain native behavior. No zero-lag/zero-leak claim is made from finite tests. See `filtering.md` for these explicit boundaries and the unchanged Parties-owned delivery limitation.
