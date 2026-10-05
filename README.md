# My Eye: The Watcher

A small Android art app. A single cartoon eye fills the screen and follows the person in front of the phone, using the front camera and on-device face detection. It is inspired by Rafael Lozano-Hemmer's *Surface Tension*.

The eye has a personality:

- **Obsessive.** It locks onto one person and ignores everyone else, even someone closer or newer.
- **Loyal.** When its person leaves, it keeps looking where they went, toward the edge if they walked off, and waits a few seconds. Someone reappearing near that spot is taken to be them.
- **Frantic when blind.** Cover the camera, turn off the lights, or deny the permission, and it searches wildly. The longer it's blind, the faster and wider it searches, and the sclera grows bloodshot.
- **Relieved.** When it sees again it grabs the first face fast, constricts its pupil, and takes one slow blink.
- **A mirror.** When its person blinks, it blinks too. Closer people dilate its pupil. Left alone, it wanders, blinks and breathes.
- **Sleepy.** After a minute with nobody around, its lids droop and it falls asleep, with "Z z z" drifting up. A long panic exhausts it into sleep too. Any face startles it awake. Asleep, it only looks at about one frame per second, to save battery.

## Privacy

Everything happens on the device. Camera frames and face data are never saved, uploaded, or logged, and the camera feed is never shown. The app's only permission is `CAMERA`; it has no `INTERNET` permission at all. ML Kit's telemetry library asks for `INTERNET`, and the manifest strips it. The camera runs only while the eye is on screen.

## Using it

- **First launch:** the app asks for the camera. Until it gets it, the eye panics.
- **Tap the eye** to ask again. If the system won't show the dialog anymore, the tap opens the app's settings page. Coming back with the permission granted works immediately.
- **Long-press** toggles the debug overlay: sight, alertness, watch state, target id, face boxes, eye-open probability, mean luma and analysis FPS. In debug builds, the chips at the bottom swap the camera for scripted fake scenarios (walk across, stranger steps closer, leaves and returns, lights off, blinker, all of them in a loop, plus two slow ones for sleep: nobody home, and covered for a long time). **Nap now** puts the eye to sleep immediately. This is how you tune on an emulator with no camera.

## Build

```bash
./gradlew assembleDebug testDebugUnitTest lint
./gradlew installDebug
./gradlew connectedDebugAndroidTest   # Compose smoke test, needs a device
```

Requires JDK 17+ and the Android SDK with API 37.

## How it works

The app is split into Gradle modules, each with one job. Arrows only point downward:

```
:app           the Activity, ViewModel, screen, debug overlay and permission flow
 ├─► :feature:eye   the eye: geometry, Canvas renderer, animation
 ├─► :core:camera   CameraX + ML Kit, and scripted fake sources
 ├─► :core:ui       the shared dark theme
 └─► :core:watch    the personality: a pure Kotlin state machine (no Android)
```

Shared build settings live in convention plugins under `build-logic/`.

```
FaceSource ──► WatcherViewModel ──► StateFlow<EyeUiState> ──► LivingEye (Compose Canvas)
 ├─ CameraFaceSource   owns ObsessiveWatcher (pure Kotlin),
 └─ FakeFaceSource     a 100 ms tick and an injected Clock
```

- `:core:camera`: CameraX `ImageAnalysis` (front, ~640×480, keep-only-latest) and ML Kit face detection (bundled model, fast, with tracking and eye classification). It emits `Perception`s: faces in normalized, mirrored coordinates plus the frame's mean luma.
- `:core:watch`: the personality, a pure state machine with time passed in. **Sight** (seeing / dark / no permission / camera error), **Watch** (Idle → Acquiring → Tracking → Lost) and **Alertness** (awake / drowsy / asleep).
- `:feature:eye`: the eye. Geometry is pure math; `CartoonEyeRenderer` draws it as a pure function of gaze, expression and style; `LivingEye` animates it.

## Tuning

Every threshold of the personality is in [`WatcherConfig`](core/watch/src/main/kotlin/com/salg/myeye/watch/WatcherConfig.kt). Change the defaults there, or pass a config to `WatcherViewModel`. Use the debug overlay (long-press) to see the numbers live.

| Field | Default | Raise it to… | Lower it to… |
|---|---|---|---|
| `darkEnterLuma` | 30 | panic in dimmer rooms sooner | only panic in real darkness |
| `darkAfterMs` | 1000 | ignore brief shadows (a hand passing) | panic the instant the lens is covered |
| `darkExitLuma` | 45 | need more light to calm down | calm down sooner (keep it above `darkEnterLuma`, or it flickers) |
| `acquireMs` | 400 | be choosier about who to watch | lock on to anyone who flashes past |
| `reliefAcquireMs` | 150 | (keep it well below `acquireMs`, or the relief feels flat) | grab the first face even faster after blindness |
| `reliefWindowMs` | 5000 | still feel relief when someone shows up long after the lights come back | relief only right after regaining sight |
| `lostTimeoutMs` | 4000 | wait longer for its person to come back | give up sooner |
| `reacquireRadius` | 0.35 | accept a returning face further from where they left (more loyal, but easier to fool) | be stricter about who counts as "them" |
| `lookAfterEdge` | 0.6 | only look after people who left right at the edge | look after anyone who left off-center |
| `lookAfterPush` | 0.25 | stare further past the edge they left by | hold closer to where they were last seen |
| `franticRampMs` | 10000 | panic builds slowly | reach full panic quickly |
| `sleepAfterMs` | 60000 | stay awake longer in an empty room | fall asleep sooner (more battery saved) |
| `drowsyLeadMs` | 15000 | a longer, more visible nod-off | drop off more abruptly |
| `exhaustAfterMs` | 60000 | panic longer at full intensity before passing out | pass out sooner when blind |
| `mirrorBlinkBelow` | 0.3 | blink with people more readily (more false blinks) | only mirror clear, full blinks |
| `mirrorBlinkRefractoryMs` | 600 | fewer mirrored blinks from flickering detection | mirror rapid blinking |
| `farSize` / `nearSize` | 0.12 / 0.45 | (the face sizes mapped to "far" and "close") | |

Face `size` is box width / image width. Read it from the overlay at the distances you care about and set `farSize`/`nearSize` from that. Mean luma is shown live too: cover the lens and read it to set `darkEnterLuma`.

The **look and motion** live elsewhere. `EyeStyle` holds proportions and colors. `EyeExpression` holds per-state lids, squint and pupil. The springs, saccade timing, blinks and frantic motion are constants at the bottom of `LivingEye.kt`.

The rationale for every default not set by the original brief is in [`DECISIONS.md`](DECISIONS.md).
