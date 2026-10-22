# <img src="docs/images/icon.png" alt="" height="36"> BetterLauncher

[![Release](https://img.shields.io/github/v/release/pierspad/BetterLauncher)](https://github.com/pierspad/BetterLauncher/releases/latest)
[![CI](https://github.com/pierspad/BetterLauncher/actions/workflows/release.yml/badge.svg)](https://github.com/pierspad/BetterLauncher/actions/workflows/release.yml)
[![License](https://img.shields.io/github/license/pierspad/BetterLauncher)](LICENSE)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3ddc84?logo=android&logoColor=white)](https://github.com/pierspad/BetterLauncher/releases/latest)

[![GitHub Sponsors](https://img.shields.io/badge/Sponsor-%E2%9D%A4-ea4aaa?logo=github&style=flat)](https://github.com/sponsors/pierspad) [![Buy Me a Coffee](https://img.shields.io/badge/Buy%20Me%20A%20Coffee-Donate-yellow?logo=buymeacoffee)](https://buymeacoffee.com/pierspad) [![Ko-fi](https://img.shields.io/badge/Ko--fi-Support-ff5e5b?logo=ko-fi)](https://ko-fi.com/pierspad)

[Features](#features) · [Screenshots](#screenshots) · [Install](#install) · [Search](#search) · [Gestures](#gestures-and-accessibility) · [Development](#development) · [Contributing](CONTRIBUTING.md)

An Android launcher based on [Olauncher](https://github.com/tanujnotes/olauncher), with customizable shortcuts, widgets, folders and optional tools to reduce distracting app use. Free software, without ads or premium reminders.

## Features

- Text-based home screen with adjustable font, size, alignment and background opacity.
- Up to eight home apps and eight customizable shortcut icons; drag to reorder.
- A home-screen widget, app folders, hidden apps and optional monochrome Lawnicons.
- App opening protected by device credentials or biometrics within the launcher.
- Optional app limits with progressive cooldowns.
- App search with three matching modes; optional contact and Android settings search.
- Custom TrueType/OpenType fonts and an app language independent of the system.
- Settings import/export through JSON files or clipboard.
- Optional double tap to lock the screen.

## Screenshots

Click a preview to see the full image.

| Choose a widget | Widget on Home | App lock |
| :---: | :---: | :---: |
| <a href="docs/images/widget_chooser.png"><img src="docs/images/widget_chooser.png" width="160" alt="System widget picker" /></a> | <a href="docs/images/widget_added.png"><img src="docs/images/widget_added.png" width="160" alt="Widget and shortcuts on the home screen" /></a> | <a href="docs/images/lock_apps.png"><img src="docs/images/lock_apps.png" width="160" alt="Choose apps to protect with device authentication" /></a> |

<details>
<summary>Settings compared with the original Olauncher</summary>

| BetterLauncher | Olauncher |
| :---: | :---: |
| <a href="docs/images/BetterLauncher.png"><img src="docs/images/BetterLauncher.png" width="200" alt="BetterLauncher settings" /></a> | <a href="docs/images/Olauncher.png"><img src="docs/images/Olauncher.png" width="200" alt="Original Olauncher settings" /></a> |

</details>

## Install

Download `betterlauncher-release.apk` from the [latest GitHub release](https://github.com/pierspad/BetterLauncher/releases/latest). Updates must use the same signing key as your existing installation.

F-Droid inclusion is being reviewed in [fdroiddata !44511](https://gitlab.com/fdroid/fdroiddata/-/merge_requests/44511). See [how F-Droid updates work](docs/f-droid.md).

## Settings backups

Export or import a JSON file from Settings → Import / Export settings. Invalid
values are rejected before replacing the saved configuration. Backups can include
a custom font, but Android permissions and widget bindings must be configured
again on the destination device. Keep exported files private: they contain your
launcher configuration, including app choices and aliases.

## Search

| Mode | Behavior | Example |
| :--- | :--- | :--- |
| Precise (default) | Consecutive fragments inside separate words, in order | `edd` → Reddit; `ton ail` → Proton Mail |
| Flexible | Query characters may occur anywhere, in order | `rdd` → Reddit |

A unique matching app opens automatically. Multiple matches remain available for
selection. Updating to 1.5.3 selects Precise once, including existing installations;
you can choose Flexible afterward. Contact search is optional and requires contact
permission. Ordinary app search works without it.

## Gestures and accessibility

Enable **Double tap to lock** in Settings → Gestures and enable BetterLauncher in Android Accessibility settings. The in-app notice includes troubleshooting help.

The optional accessibility service also enforces configured app cooldowns. A separate disclosure explains its access before activation. Accessibility data is processed on the device. You can decline or disable the service and keep using ordinary launcher features.

App lock protects launches through BetterLauncher; it does not prevent another application or Android itself from opening the target app.

## Development

Use JDK 17, Android SDK 36 and the included Gradle wrapper:

```sh
./gradlew assembleDebug assembleDebugAndroidTest lintDebug
```

The debug app has a separate `.debug` package. See [CONTRIBUTING.md](CONTRIBUTING.md) for emulator regression checks and the release workflow. Google Play release automation is described in [docs/google-play-release.md](docs/google-play-release.md).

## Contributing

Bug reports, translations and focused pull requests are welcome. Please discuss substantial changes in an issue first. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Acknowledgements

Based on [Olauncher](https://github.com/tanujnotes/olauncher), with monochrome icons from [Lawnicons](https://github.com/LawnchairLauncher/lawnicons). Their respective licenses and attribution remain in the source.

## AI disclosure

Development and documentation have been assisted by language models. Contributions still require review and verification.

## License

[GNU GPL v3](LICENSE).
