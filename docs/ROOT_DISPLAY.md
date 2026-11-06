# Castix screen modes: Extinguish, system lock and AMOLED fallback

Status: **experimental**. None of the Root display, ColorOS wake gesture, or
locked-player compatibility paths has yet been validated on a real OnePlus 15.
The repository's Debug build and unit tests must pass before merging.

## Why there are three mutually exclusive modes

| Mode | Real physical panel power | Android locked? | Expected playback behavior |
|---|---|---|---|
| `PHYSICAL_OFF` (default) | OFF via Root + Extinguish | **No** | Current player normally stays resumed |
| `SYSTEM_LOCK` | Controlled by Android/ColorOS sleep | **Normal secure keyguard** | Depends on the player; LSPosed audio guard may help |
| `AMOLED_OVERLAY` | ON with black overlay | No | Old manual fallback, no automatic fallback on Root errors |

**Physical panel-off is not a privacy or password lock.** The app stays active,
and notification/security policies do not automatically change. This is the
recommended mode for users prioritizing uninterrupted video playback.

**Normal system lock never lies to third-party apps about their lock state.**
The Root `input keyevent 223` command requests genuine Android `KEYCODE_SLEEP`.
Android applies lockscreen credentials and lock delay according to device settings;
configure a secure screen lock and immediate lock-after-sleep if this is important.
LSPosed audio hooks are separately scoped and opt-in. A player that pauses in
`onStop()`, destroys its Surface or calls `pause()` may still stop.

## Upstream attribution and code boundaries

The physical display-power approach is derived from
[Moderpach/Extinguish](https://github.com/Moderpach/Extinguish)
and the user's fork [yagay/Extinguish](https://github.com/yagay/Extinguish),
licensed GPL-3.0. Keep that attribution when merging. This code is isolated
under `app/src/main/java/me/elhizazi/Castix/display` to permit future upstream
updates without importing the full Extinguish UI or Shizuku services.

- `RootDisplayCli.java`: a privileged `su` / `app_process` helper calls
  `SurfaceControl.setDisplayPowerMode(token, 0 / 2)`; Android 14+ token path
  follows Extinguish's `DisplayControl` services.jar code.
- `RootDisplayManager.kt`: CPU awake host, bounded partial WakeLock, Root
  getevent wake monitor, Root recovery timer, display-state lifecycle.
- `InputGuardHost.kt`: transparent, full-screen input-consuming overlay only
  during Root physical panel-off. **It is not a black-image or brightness
  approximation.** It prevents taps reaching the application content where
  Android allows this overlay. OS gesture navigation and sensitive system
  surfaces may still handle touches.
- `RootDisplayPreferences.kt`: selected mode, migration from prior Root
  boolean, and safety-test setting.
- `ScreenModeController.kt`: one mode dispatcher for UI button, notification
  action, floating button and Quick Settings tile; system lock uses Android's
  own keyguard and sleep state, not fake PowerManager/KeyguardManager responses.

**Why not grab all Linux touch devices?** An evdev `EVIOCGRAB` can suppress
vendor-native gestures and system navigation, and a crash may leave input
blocked. The Root getevent watcher is *read-only*. A temporary transparent
Android input layer is used solely as a safety boundary around the foreground
application, while the actual display power remains Root controlled.

## Recovery: leave the 30-second test enabled

1. Grant Root through your manager, then allow Castix's overlay permission.
2. Select `PHYSICAL_OFF`. Keep **30-second safety recovery test** enabled.
3. Open a target video player, then activate the Castix screen action.
4. Try the device's native double-tap gesture first, if it emits a wake event.
   A touch double-tap inside the guard and double volume-up are fallbacks.
5. If no input is delivered while the panel is OFF, the detached Root watchdog
   should attempt restoration after 30 seconds. The app also has an independent
   timer to request restoration.
6. Only after successful device tests turn off the 30-second test. A two-hour
   Root watchdog still limits the duration of an unresponsive display session.

Native ColorOS DT2W may require real Android sleep; it is **not guaranteed**
to function when Android is still interactive and only the display power mode
is OFF. A software input listener cannot manufacture a missing touch IRQ.

If an attempt to enter display-off fails, the code tries Root restore, reports
the failure, and removes the invisible input guard so a visibly powered display
does not become untouchable. If Root restore itself fails, the watchdog remains.

### Emergency root/ADB display restore

```sh
su
APK="$(pm path me.elhizazi.Castix | head -n 1 | sed 's/^package://')"
CLASSPATH="$APK" /system/bin/app_process /system/bin me.elhizazi.Castix.display.RootDisplayCli on
```

If this hidden API fails on the current ColorOS build, a reboot may be
necessary. Before testing, ensure you have a reliable ADB or hardware recovery
route and that the device is not needed for an emergency task.

## Build and verification

The debug-only feature-branch workflow runs:

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

Before merging, test all of the following on-device: video stays playing with
the physical panel off; taps do not activate the underlying video player;
recovery via both native gestures and fallback hardware keys; timer restoration;
process death and failed Root permission; normal lock showing the real keyguard;
AMOLED manual fallback; and changing modes only when the screen is restored.

**GitHub source commits and a mergeable PR are not proof of APK build or
device compatibility.**
