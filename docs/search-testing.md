# Precise search regression checks

Precise is the default. Each query fragment must occur consecutively inside a
separate word of the app label, in the same order. Matching ignores case and
accents; spaces and punctuation separate words. Words may be skipped. Flexible
search remains an explicit alternative. The first upgrade to 1.5.3 resets all older
mode selections to Precise once. Explicit choices made afterward survive process
recreation and new backups. Older backups without the migration marker also
resolve to Precise.

Automatic opening still requires exactly one result and a launchable app. An
ambiguous query must never open the highest-ranked result automatically. Enabled
contacts/settings search results also count toward ambiguity. An initial space
suppresses automatic opening, and IME composition retains its existing protection.

## Automated checks

Run the matcher without Android (requires Kotlin and Java):

```sh
bash scripts/test-search.sh
```

Build and check Android integration with an emulator or an authorized test phone:

```sh
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
bash scripts/run-android-regressions.sh
```

The device runner installs the debug app and test APK, then runs 13 regression
groups. Its search checks use synthetic app names and capture launch callbacks,
so no real mail, VPN or Reddit app needs to be installed or opened.

## Manual checks

Open the drawer's search options and select Precise. Actual installed app names
and enabled secondary search sources determine whether a query is unique.

- `Prot`: Proton Mail matches; Pedometro - Contapassi does not. With Proton VPN
  also installed, both Proton apps match and neither opens automatically.
- `edd`: Reddit matches; a second app called Reddit News makes it ambiguous.
- `pro ma` and `ton ail`: Proton Mail matches; Proton VPN does not.
- `rdd`, `ail ton` and `pro ton`: Reddit/Proton Mail do not match in Precise.
- Flexible: `rdd` can match Reddit again.
- Paste a complete multiword query to test it without an earlier unique fragment
  opening an app first. Alternatively start with a space to inspect results
  without automatic opening.
- In Settings, tap the star: it must open the BetterLauncher Play listing with
  package ID `com.pierspad.betterlauncher`.

## Accessibility consent

Open the accessibility permission entry in Settings while consent is not yet
accepted. In portrait and landscape, the title and both actions must stay visible;
scrolling moves only the disclosure text. Declining must close the dialog without
opening Android settings. Automated layout checks cover 280, 320 and 480dp heights.
