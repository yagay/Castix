# Castix playback backend status

This document describes the implemented behavior of the `fix/real-playback-backends` branch.
A successful shell exit code is not proof that a third-party video player will continue
playing after its Activity stops or its video Surface disappears.

## Backend capabilities

| Backend | Commands | Scoped playback hook |
|---|---|---|
| Root | Real `su -c` with timeout and no unprivileged fallback | No |
| Shizuku | Uses deprecated, authorization-checked `Shizuku.newProcess` when available | No |
| Dhizuku | Unsupported for arbitrary commands (returns error) | No |
| LSPosed | Commands unsupported; module may install the optional AudioManager hook | Experimental |
| LSPatch | APK patch/command operations unsupported (returns error) | No |
| Accessibility | Foreground package observation only; no policy bypass | No |

## Experimental LSPosed audio focus guard

1. Install this APK and add the selected media application to Castix's LSPosed scope.
2. Start the app, choose the target apps and enable the experimental audio focus guard.
3. Restart each selected scoped app to ensure the Xposed entrypoint is invoked.
4. The module hooks `AudioManager.abandonAudioFocus(...)` and (API 26+)
   `AudioManager.abandonAudioFocusRequest(...)`.
5. At invocation, the hook asks Castix's read-only provider whether the calling app
   is selected and opted in. A two-second cache avoids high-frequency Binder calls.
6. Disable the guard if audio focus is not released properly. Restart the target app.

The provider verifies that the calling UID owns the requested package and returns only
a boolean about that calling package. It does not expose the selected package list.

**Limits:** The hook does not prevent the app from calling `pause()`, closing a
MediaCodec, releasing Surface/TextureView, or responding to `Activity.onStop()`.
It may disturb audio focus arbitration; the guard is **off by default**.
An active LSPosed module status in Castix does **not** prove the target app is hooked.

## Root and Shizuku playback tasks

- `BATTERY_WHITELIST`: add one selected application to the Doze whitelist.
- `STANDBY_BUCKET_ACTIVE`: request the Active standby bucket.
- `APP_OPS_BACKGROUND`: request appops background/WAKE_LOCK allowance.
- `AUDIO_FOCUS_HIJACK`: unsupported as a shell task; use the opt-in LSPosed guard.
- `PHANTOM_KILLER_DISABLE`: unsupported, because the original command changed
  global system settings and was not a safe per-target playback optimization.

Tasks are executed only for the packages selected by the user, sequentially. Failures
and unsupported operations are logged with nonzero exit codes. Commands may be
rejected by vendor ROMs; no automatic success should be inferred.

Shizuku's `newProcess` API is deprecated and may fail on newer Shizuku versions.
The longer-term solution is a bound Shizuku `UserService` with explicit Binder IPC.

## Screen-off background service

- Castix is a screen/overlay controller, not the media player, so its foreground
  service now declares `specialUse` rather than falsely claiming `mediaPlayback`.
- A partial CPU WakeLock is requested only after real `ACTION_SCREEN_OFF` (or
  when the service starts while the device is already non-interactive).
- It is released on screen-on, user-present, or service teardown. A two-hour
  maximum timeout guards against a permanently held lock after a missed event.
- This CPU lock is not a replacement for the actual player's MediaSession,
  foreground media playback service or app-specific lockscreen support.

## Manual validation

- Turn on LSPosed for Castix and one test player; inspect module logs.
- Verify the hook is inert when the opt-in guard is disabled.
- Enable it and confirm only the selected target's audio-focus abandon is intercepted.
- Test playing, stopping, headphones/Bluetooth disconnect, phone calls, screen lock,
  Picture-in-Picture and app switch; ensure other apps are not disrupted.
- Test Root commands with a permitted and a denied `su` grant.
- Test Shizuku authorized/unauthorized and server-offline cases.
- Test a ColorOS device separately: a normal lock screen changes Activity and Surface
  lifecycle and is not equivalent to an AMOLED overlay or Extinguish display power-off.
- Verify the Debug build and unit tests through CI before merging this branch.

## Build

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest --no-daemon
```

The project compiles against legacy Xposed API 82 using `compileOnly`; runtime API
classes are provided by LSPosed, and must not be packaged inside the APK.
