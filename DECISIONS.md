# Decisions

Defaults chosen while building The Watcher that the original brief did not specify. Newest milestone at the bottom.

## Milestone 1: Baseline

- **Versions (checked against Google Maven / Maven Central on 2026-09-26):** AGP 9.4.1 (later pinned back to 9.2.1, see the bottom of this file), Kotlin (compose compiler plugin) 2.4.20, Compose BOM 2026.09.00, lifecycle 2.11.0, activity-compose 1.13.0, core-ktx 1.19.1. The template's other versions were already current.
- **Gradle wrapper 9.4.1 → 9.8.0.** AGP 9.4.1 refuses to run on Gradle below 9.6.0, so I moved the wrapper to the latest stable release (checksum pinned) instead of holding AGP back.
- **compileSdk/targetSdk 36 → 37.** Android 17 (API 37) is a stable, non-preview platform in the local SDK, and the test device (Pixel 7 Pro) runs it. The brief said to raise these if a newer stable level is out. minSdk stays 31.
- **Kotlin version = compose compiler plugin version.** With AGP's built-in Kotlin, applying `org.jetbrains.kotlin.plugin.compose` at 2.4.20 also sets the Kotlin compiler version. There is no separate `kotlin-android` plugin.
- **Theme is always dark, with dynamic color removed.** The artwork needs a fixed near-black stage (`#0B0B0D`) no matter the wallpaper or system theme. The window background and splash background use the same color, so the app never flashes white on launch.
- **Portrait lock via `android:screenOrientation="portrait"`.** On large screens (sw ≥ 600dp) Android 16+ ignores orientation locks for apps targeting 36+. That's acceptable because the ViewModel owns all watcher state and survives rotation anyway.
- **`.idea/` is ignored entirely.** Android Studio rewrites it with machine-specific state (JDK paths, deployment targets), so it doesn't belong in the repo.

## Milestone 2: Cartoon eye

- **Pupil mapping:** `expression.pupil` (0..1) maps linearly to 30–70% of the iris radius, so the brief's values (0.35–0.8) land inside the intended 40–60% band, with Frantic slightly beyond it for a dilated look.
- **Sclera:** 86% of screen width, aspect 1.3 (width/height), outline 5% of the horizontal radius. The iris gets a darker limbal ring (35% toward black) for depth.
- **Lids:** two quadratic curves between the eye corners, intersected with the sclera oval (`Path.combine(Intersect)`). Everything is clipped to that aperture, and the aperture is what gets outlined. Closed = both curves meet slightly below center. The upper lid does most of the travel; the lower lid follows `sqrt(lidOpen)`. Squint lowers the upper lid by 0.35·ry and raises the lower by 0.5·ry. A faint shadow is cast under the upper lid.
- **Foreshortening:** `squash = 1 − 0.3·|gaze|²`, applied along the gaze direction (quadratic, so the iris stays round near the center).
- **Animation constants not in the brief:** saccade spring (ζ 0.9, k 900), acquire snap (ζ 0.75, k 1200), lid spring (ζ 0.9, k 400), pupil spring (ζ 1, k 120; pupils are slow), dead zone 0.03 normalized, blink 70 ms close / 120 ms open, frantic springs ζ 0.55, tremor amplitude 0.008–0.028. Lost: holds 1.2 s, then glances ±0.2 x / ±0.1 y every 0.5–1.1 s. Idle drift: 0.06 around each saccade target. Blinks every 3–7 s in every calm state (not just Idle); never while frantic.
- **Mirror blink** keeps the eye closed for as long as the person's eyes are closed, instead of playing a fixed-length blink.
- **Relief** skips the `reliefKey` value present at first composition, so recreating the UI never replays an old relief.
- **Immersive mode:** system bars are hidden (swipe to reveal transiently) with dark bar styling. The status bar icons were unreadable on the black stage.
- **Touch normalization:** finger position is normalized to the screen's half-width/half-height, so a touch at a screen edge is full deflection.

## Milestone 3: State machine

- **`onTick(nowMs)` alongside `onPerception`.** After a single `NoPermission` nothing new arrives, yet panic must keep escalating and Lost must still time out. The ViewModel ticks the watcher; it stays pure (time is always passed in).
- **The watcher keeps its current state internally** (`ObsessiveWatcher.state`), but every transition is a deterministic function of (previous state, input, `nowMs`).
- **Going blind resets Watch to Idle.** When it sees again, it has to find someone, which is what drives the relief moment.
- **Relief = the first acquisition that *starts* within `reliefWindowMs` (5 s) of regaining sight.** That acquisition uses 150 ms and emits `relief` once on reaching Tracking. If nobody shows up within 5 s, the next person gets the normal 400 ms with no relief. Re-acquiring during Lost goes straight to Tracking (per brief), with no relief.
- **The initial Sight is SEEING (Idle),** so the app doesn't flash frantic during the ~hundreds of ms the camera takes to start. Granting permission later therefore counts as regaining sight and earns a relief moment.
- **Acquiring → Idle when the candidate vanishes,** even if other faces are present; the next frame picks a new candidate. (Strictly per brief, and it costs only one frame.)
- **Lost re-acquire picks the nearest face within 0.35,** not the first one found.
- **At exactly 4000 ms Lost times out before re-acquire is considered** (`>=` boundaries throughout: 400 ms, 1000 ms, 4000 ms).
- **Mirror blink uses a strict `< 0.3`,** and unknown eye-open (`null`) never blinks.
- **Closeness:** face size 0.12 → 0 and 0.45 → 1 (clamped), configurable in `WatcherConfig`. Pupil = 0.4 + 0.25·closeness.
- **`Clock`, `Gaze`, `SeenFace`, `Perception` live in `watch/`** so the pure layer has no dependency on `camera/` or Compose.

## Milestone 4: Fake source + ViewModel

- **Coroutines 1.11.0 declared explicitly** (`kotlinx-coroutines-android`). Lifecycle only brings 1.9.0 transitively, and `kotlinx-coroutines-test` must match the runtime version. Turbine 1.2.1.
- **Perceiving is tied to UI collection:** `uiState` uses `stateIn(WhileSubscribed(stopTimeoutMillis = 0))` and the UI collects with `collectAsStateWithLifecycle()` (STARTED). So the camera stops the instant the app leaves the screen, and starts again on return. A non-zero timeout would keep the camera open in the background, where Android revokes it anyway. Cost: a configuration change restarts the camera, which is rare with the portrait lock.
- **The watcher, relief counter and FPS meter live in the ViewModel, not in the flow,** so its memory (who it was watching, how long it has been blind) survives the UI going away and coming back.
- **Tick every 100 ms** (`WatcherViewModel.TICK_MS`) for time-based transitions. `StateFlow` dedups identical states, so idle ticks don't recompose anything.
- **Permission is reported to the ViewModel by the UI** (`onCameraPermission`). Unknown (`null`) = perceive nothing and stay calm, so there's no panic flash before the first check. Denied = a single `NoPermission`; the tick drives the escalation.
- **The fake source runs at 15 fps (66 ms),** about what low-res ImageAnalysis + ML Kit FAST manages on a mid-range phone. Scenario ids change every loop (and per scenario in the Tour), as ML Kit's would.
- **Fake scenarios:** walk across, stranger steps closer, leaves and returns (re-acquire with a new id, then Lost → Idle), lights off (DARK → frantic → relief with a new id), blinker (mirror blink, including a long blink), plus a **Tour** that plays them all back to back. `FakeScenariosTest` checks each one really provokes its behavior.
- **Temporary:** this milestone defaults the app to the fake Tour, since the camera arrives in milestone 5.

## Milestone 5: Camera, permission, overlay

- **CameraX 1.6.2, ML Kit face-detection 16.1.7 (bundled model).** `ProcessCameraProvider.awaitInstance` is an extension in 1.6, so it needs an explicit import.
- **The camera binds to a private `LifecycleOwner` owned by the flow collection,** not to the Activity. Collecting `CameraFaceSource.perceptions()` sets it to RESUMED and binds; cancelling unbinds, destroys it, closes the detector and shuts down the analysis executor. Combined with `WhileSubscribed(0)`, the camera runs exactly while the eye is on screen, and the ViewModel never holds an Activity.
- **Resolution:** `ResolutionStrategy(640×480, FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)`. Analysis runs on a dedicated single-thread executor. Detector callbacks run on a direct executor, so `ImageProxy.close()` always happens even after the analysis executor has shut down.
- **Detection failures drop the frame silently** (no Frame is sent). Logging was avoided on purpose, so nothing about frames ends up in logcat.
- **Camera state errors** (another app holding the camera, camera disabled by policy, …) are reported as `CameraError` → frantic. Frames arriving again restore sight on their own.
- **`eyesOpen` = the lower of the two eye probabilities** (one eye shut counts as shut). It's null only if both are unknown.
- **Merged manifest privacy:** ML Kit pulls in `com.google.android.datatransport` (Firelog telemetry), which adds `INTERNET` and `ACCESS_NETWORK_STATE`. Both are removed with `tools:node="remove"`, so its uploads can never leave the device. The merged manifest's only permission is `CAMERA`, plus `com.salg.myeye.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: a *signature* permission that AndroidX Core declares and grants only to this app itself (used by `ContextCompat.registerReceiver` on API < 33). It grants no access to anything, and removing it can break receivers on API 31–32, so it stays.
- **`uses-feature`:** front camera required, back camera not required. Declaring `CAMERA` would otherwise imply the back camera.
- **Permission flow:** the request happens on first launch, once (`rememberSaveable`). The state is re-checked in `LifecycleResumeEffect`. Tapping the eye re-asks while `!asked || shouldShowRequestPermissionRationale`, and otherwise opens the app's Settings page. While the dialog is up, the eye already panics (it can't see yet), and granting brings the relief moment.
- **Overlay:** toggled by long-press (`combinedClickable` with no indication, so it's accessible and testable), and visible state is kept with `rememberSaveable`. Face boxes are drawn in normalized space as squares with half-size = `size` (the true box aspect isn't kept in `SeenFace`). The fake-source picker is shown only when `BuildConfig.DEBUG` (`buildConfig` feature enabled for this).
- **Smoke test** replaces the template's instrumented example: it renders `WatcherScreen` from a fixed state, long-presses to show and hide the overlay, and checks that taps are forwarded.
- **Found on device:** someone looking down at the phone gets eye-open probabilities < 0.3 continuously, so a held mirror blink keeps the eye shut. This is addressed in milestone 6.

## Milestone 6: Polish

- **Mirror blink is an event, not a held state.** The watcher emits a one-shot `mirrorBlinkStart` on the closing edge (eyes-open falls below 0.3 for the same tracked id). The ViewModel turns it into `blinkKey`, and the eye plays one 70/120 ms blink. On device, someone looking down at the phone is classified "closed" for seconds, which used to hold the eye shut. Now they get one blink.
- **Mirror blink refractory period: 600 ms** (`WatcherConfig.mirrorBlinkRefractoryMs`), because ML Kit's FAST eye classification flickers around the threshold.
- **Frantic escalation adds strain:** a bloodshot sclera (`EyeExpression.strain`, `EyeStyle.strainedSclera` `#F0B9AE`) that follows `intensity²`. It builds on a slow spring (k = 20) and fades over 2.5 s once it can see again. The saccade rate, reach, spring stiffness and tremor still escalate exactly as briefed.
- **Idle life:** the lids "breathe" (±0.02 lidOpen, 5.3 s period) and the pupil hunts slightly (hippus, ±0.025, 3.1 s) in every state. 15% of idle saccades are curious long glances (reach 0.75, edge-biased), and 15% of spontaneous blinks are double blinks.
- **Relief moment** is unchanged from milestone 2 (pupil to 0.35 + one slow 320/520 ms blink, then the tracking expression resumes). It was verified on device by denying and then granting the camera.
- **Final merged manifest (debug and release):** `CAMERA` only, plus the app-private signature permission `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (see milestone 5). No `INTERNET`, no `ACCESS_NETWORK_STATE`.

## Iteration: Android Studio compatibility

- **AGP pinned to 9.2.1, back from 9.4.1.** Android Studio 2025.3.4 (the IDE used for this project) bundles AGP 9.2.1 and refuses to sync projects that use a newer AGP. 9.4.1 built fine with `./gradlew` but broke Sync/Run in the IDE. **Rule: don't raise AGP above the version bundled with the installed Studio** (`Android Studio.app/Contents/plugins/android/lib/libagp-version.jar` → `version.properties`). The Gradle wrapper stays on 9.8.0; AGP 9.2.1 works with it.

## Iteration: Sleep & wake (`feature/sleep`)

- **Your choices:** sleep after 1 min alone, a long panic exhausts it into sleep, asleep = closed lids + floating "Z z z", battery = analyze ~1 frame/s.
- **Alertness is a third axis on top of Sight and Watch.** Alone = seeing, Watch Idle, and *no face at all* in the frame. A stranger resets it, and Lost (waiting for its person) doesn't count. Drowsy = the last `drowsyLeadMs` (15 s) before `sleepAfterMs` (60 s). Exhausted = blind for `franticRampMs + exhaustAfterMs` (10 s + 60 s).
- **Asleep overrides everything.** Darkness, no permission and camera errors are still measured (the overlay shows them) but cause no panic. Only a face wakes it. Waking emits a one-shot `wake` and goes to Acquiring on the largest face (normal 400 ms). The startle replaces relief, so relief never plays on waking.
- **No `nowMs` in `WatcherState`.** A field that changes on every 100 ms tick would make `StateFlow` emit (and the screen recompose) ten times a second even when nothing visible changed. The overlay therefore shows drowsiness % rather than an alone timer.
- **The low-power contract is part of `FaceSource`:** `perceptions(lowPower: StateFlow<Boolean>)`, with `LOW_POWER_INTERVAL_MS = 1000`. The camera drops frames *before* luma and ML Kit (the main cost) via the pure, tested `shouldSkipFrame`; the camera itself keeps running, with no rebind, so waking is at most ~1 s. The first frame is always analyzed. A first version used `Long.MIN_VALUE` as "never analyzed", which overflowed and dropped every frame while asleep, so it could never wake. It was caught on the device and is now covered by a test.
- **Sleep look:** lids close over a slow 1.8 s tween (not the usual spring), and the gaze rolls down to (0, 0.3). Three stroked "Z" glyphs (pure `zzzGlyphs`, anchored to the sclera) rise, grow and fade on a 4.2 s loop. Lid breathing is disabled while asleep so it never cracks open. **A closing lid edge lightens toward the sclera color**, because the dark outline vanished on the dark stage and a sleeping eye was just Zs.
- **Drowsy look:** lids droop 0.8 → 0.2 with drowsiness, the gaze sinks (y 0.15 → 0.5) in slow 2.5–4 s drifts, and blinks become slow and heavy (260 ms close, 0.1–0.4 s hold, 450 ms open) every 2–4 s.
- **Startle:** blink is cancelled, the lids spring to 1.0 with ζ 0.4 / k 1500 (overshoot), the pupil snaps to 0.3 in 120 ms, and a 300 ms jolt (±0.03 tremor) plays. After 700 ms the resting expression takes over again. Relief and startle share one "one-shot owns the face" guard.
- **Fake scenario clock drifts:** `FakeFaceSource` advances scenario time by its own delays, which run a little slow on the main thread (~5%). So "visitor at 75 s" arrives a few seconds late in real time, while the watcher's timers use the real clock. That's harmless for tuning and kept for virtual-time-friendly tests. The two sleep scenarios are excluded from Tour because each runs well over a minute.
