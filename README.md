# My Eye: The Watcher

A small Android art app. A single cartoon eye fills the screen and follows the person in front of the phone, using the front camera and on-device face detection. It is inspired by Rafael Lozano-Hemmer's *Surface Tension*.

The eye has a personality:

- **Obsessive.** It locks onto one person and ignores everyone else, even someone closer.
- **Loyal.** When its person leaves, it keeps looking where they went and waits for them to come back.
- **Frantic when blind.** Cover the camera, turn off the lights, or deny the permission, and it searches wildly. The longer it's blind, the worse it gets.

## Privacy

Everything happens on the device. Camera frames and face data are never saved, uploaded, or logged, and the camera feed is never shown. The app's only permission is `CAMERA`, and it has no `INTERNET` permission at all.

## Build

```bash
./gradlew assembleDebug testDebugUnitTest lint
./gradlew installDebug
```

Requires JDK 17+ and the Android SDK with API 37.
