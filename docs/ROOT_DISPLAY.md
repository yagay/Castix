# Root physical display power (Extinguish integration)

Status: **experimental; not yet verified on ColorOS or OnePlus 15**.

This implementation ports the physical display-power technique from
[Moderpach/Extinguish](https://github.com/Moderpach/Extinguish)
(and the user's fork [yagay/Extinguish](https://github.com/yagay/Extinguish))
into a **separate Root back end** in Castix. Upstream Extinguish is GPL-3.0
and Castix is GPL-3.0-or-later. Retain this credit when merging source.

## What this changes

- `RootDisplayCli.java`: privileged `app_process` process started through `su`.
  Calls hidden `SurfaceControl.setDisplayPowerMode(token, 0/2)`.
  Android 14+ display token follows Extinguish's `DisplayControl` services.jar route.
- `RootDisplayManager.kt`: lifecycle, UID-0 permission check, 1x1 KEEP_SCREEN_ON
  view (not a black screen), root `getevent` watcher, safety timeouts and recovery.
- `RootDisplayPreferences.kt`: choose Root physical display or existing AMOLED
  overlay. Root mode is preferred by default; overlay must be chosen explicitly.
- The quick tile, notification, floating trigger and main black-screen action
  all use the selected display mode.
- LSPosed audio guard remains separate and opt-in. Physical panel-off does
  not require disabling normal Activity lifecycle callbacks.

## Safety and use

**Start with the default 30-second safety test enabled.**
The root bridge is experimental, and a vendor implementation may reject
the hidden APIs or fail to emit touch events while the panel is off.

1. Grant Castix Root from KernelSU / Magisk / APatch.
2. Grant overlay permission; the 1x1 keep-awake host needs it.
3. Select **Real screen power-off (Root / Extinguish)** in AMOLED settings.
4. Leave **30-second safety recovery test** enabled and start a video.
5. Turn the screen off using the Castix floating trigger or main action.
6. Try system double-tap (if events exist), **double volume up**, or power
   button; the display should be restored automatically within 30 seconds.
7. Only after several successful tests disable test mode. A detached root
   watchdog will still attempt to restore after two hours.

**Important:** The code cannot force the ColorOS touch controller to produce
touch events when the panel is physically off. The hardware's native double-tap
wake may only be enabled in the *real* Android sleep state. In that case
physical off + keep-running apps and native DT2W cannot both be guaranteed.

Do not rely on these paths for alarm, emergency, accessibility or security
workflows until tested on the actual ROM. The root evdev listener is a
best-effort fallback, not a guarantee. The app does not bypass or unlock the
Android keyguard.

### Emergency restoration from an ADB/root terminal

If the display remains physically off, connect with adb or use a previously
available root terminal. Run these commands **inside an interactive shell**:

```sh
su
APK="$(pm path me.elhizazi.Castix | head -n 1 | sed 's/^package://')"
CLASSPATH="$APK" /system/bin/app_process /system/bin me.elhizazi.Castix.display.RootDisplayCli on
```

If the display bridge reports an error, restarting the phone may be necessary.
The watchdog file is under `/data/local/tmp/castix_display_<app_uid>.session`;
its background timer restores the screen only if the same session marker remains.

## Architecture boundaries

```text
Castix UI / mode selection
    |         |
    |         +-- optional AMOLED black overlay (manual fallback)
    |
    +-- CastixBackgroundService (keeps session alive)
            |
            +-- RootDisplayManager
                    |-- tiny KEEP_SCREEN_ON host
                    |-- root getevent input reader (double tap / keys)
                    |-- out-of-process recovery watchdog
                    |
                    +-- su -c CLASSPATH=<Castix APK> app_process ... RootDisplayCli
                            |
                            +-- Extinguish SurfaceControl/DisplayControl method
```

The new display code is intentionally isolated in `me.elhizazi.Castix.display`
so a future Extinguish source update only requires reviewing the bridge rather
than merging all of Extinguish's UI, Shizuku IPC and settings into Castix.

## Remaining compatibility work

- Runtime test that the `services.jar` classloader and `android_servers`
  JNI loading remain accessible on ColorOS/OxygenOS 16.
- Confirm physical display token mapping and multi-display behavior.
- Confirm root `getevent -l` reports `KEY_WAKEUP` or `BTN_TOUCH` when
  `POWER_MODE_OFF` is active. If not, inspect OPlus native gesture HAL and
  kernel; never assume LSPosed can manufacture unavailable touch IRQs.
- Confirm user app truly stays resumed and is not paused by the player itself.
- The detached watchdog is intended as a last resort; verify that it survives
  Android process death on the target ROM.
- Verify Debug compilation and tests before merging. No APK has been verified
  merely by pushing these commits.
