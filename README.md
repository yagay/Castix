# Castix

Castix is an Android utility for managing background playback and related playback restrictions through several privilege/accessibility backends.

## Features

- Multiple backend options: **Shizuku, Dhizuku, Root, LSPosed, LSPatch, and Android Accessibility**.
- Backend status and permission checks from the main interface.
- Target-app selection for supported playback workflows.
- AMOLED / black-screen mode with configurable clock styles.
- Customizable clock scale, vertical position, and spacing.
- Quick Settings support for the black-screen mode.
- Multilingual interface, including Arabic, English, Chinese, Japanese, French, Spanish, Portuguese, German, Italian, Russian, and Turkish.
- No Firebase or Google Play Services dependency in the current build.

## Requirements

- Android 7.0 (API 24) or newer.
- Some features require an appropriate backend to be installed and configured on the device.
- Root, Shizuku, Dhizuku, LSPosed, LSPatch, and Accessibility features are optional; Castix does not require all of them at the same time.

## Language support / 语言支持

Castix supports **English** and **简体中文** for the Root/Extinguish screen modes,
secure lock, home controls, Quick Settings tile, and related error/status
messages. Language selection is available under Settings; **Auto** follows
the Android system language (Chinese system locales use Simplified Chinese).

Root and LSPosed command identifiers, Android class/method names, and raw
diagnostic shell output are not translated. Existing community translations
remain available; new feature strings use English fallback where those
translations have not yet been extended.

新增 Root/Extinguish 物理熄屏、系统锁屏、首页操作、快捷设置磁贴及相关提示均支持
**简体中文和英文**。可在应用设置中选择语言，使用“自动”时跟随系统语言。
命令、类名和原始诊断日志仍保留其技术原文，旧版其他语言翻译继续保留。

## Screen control modes (experimental branch)

Castix offers three mutually exclusive screen actions. See
[Root display and lock-mode details](docs/ROOT_DISPLAY.md) and the
[actual privilege backend matrix](docs/PLAYBACK_BACKENDS.md).

- **Physical screen-off without Android lock (default)** — Root invokes
  Extinguish-derived physical display power control. The player normally
  remains in the foreground. A transparent input guard prevents app-area
  accidental taps, and a safety test attempts automatic restoration after
  30 seconds. **This mode does not protect the device with a keyguard.**
- **Normal system lock** — Root sends Android's KEYCODE_SLEEP. ColorOS/Android
  handles its normal sleep, keyguard and authentication. No fake "unlocked"
  status is injected into third-party applications. Player continuation
  depends on the application and device. Configure a secure lock screen.
- **AMOLED overlay** — the original black-overlay mode is only an explicit
  manual fallback, never a hidden substitute for a failed Root command.

Physical panel-off, native double-tap wake and normal-lock video playback are
not yet verified on the OnePlus 15 / ColorOS. Test with recovery prepared
before relying on these features.

## Building

### Android Studio

1. Clone or download this repository.
2. Open the project in Android Studio.
3. Allow Gradle to synchronize the project.
4. Build the desired variant from the Gradle/Build menu.

### Command line

Debug build:

```bash
./gradlew assembleDebug
```

Release build:

```bash
./gradlew assembleRelease
```

The release configuration used for distribution may require the signing environment variables configured by the project maintainer. F-Droid builds should use their own signing process.

## License

Castix is licensed under the **GNU General Public License v3.0 or later**.

Copyright (C) 2026 Jamal El Hizazi.

See the [`LICENSE`](LICENSE) file for the complete license text.

## Third-party components

Castix uses open-source Android libraries and the Shizuku API/provider. Their respective licenses remain applicable to those components. See the project dependency declarations and the upstream projects for their individual license terms.

## Disclaimer

Castix is provided without warranty. Availability and behavior of privilege backends depend on the Android version, device configuration, installed services, and the permissions granted by the user.

## Third-party compatibility APIs

Castix compiles against the legacy Xposed API as a `compileOnly` dependency;
the runtime API comes from LSPosed. Local Xposed API stubs were removed.
The opt-in audio-focus hook is experimental and does not ensure that an app's
video playback survives Activity stop or Surface teardown. Dhizuku and LSPatch
command execution remain unsupported until real backends are implemented.
See `THIRD_PARTY_NOTICES.md` and `docs/PLAYBACK_BACKENDS.md`.
