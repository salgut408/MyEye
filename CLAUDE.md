# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

"My Eye" (`com.salg.myeye`) is **The Watcher**: a single-screen art app where a cartoon eye follows the viewer using the front camera and on-device ML Kit face detection. See `README.md` for the concept and `DECISIONS.md` for the defaults chosen along the way (add to it when you pick a new default).

It is a single `:app` module with a single Activity and Compose only. There is no DI framework (constructor injection by hand), no navigation, and no network. **Privacy is a hard requirement:** never log, save, or upload frames or face data, and the merged manifest must contain only `CAMERA`, never `INTERNET`.

What exists so far (keep this updated as the architecture lands):

- `ui/eye/` is the eye, in three layers. `EyeGeometry.kt` is pure, JVM-tested math (sizes, iris travel/foreshortening, lid positions, fixed highlights). `EyeRenderer` is a swappable `DrawScope` renderer (`CartoonEyeRenderer`) that's a pure function of `(gaze, EyeExpression, EyeStyle)`. `LivingEye` is the animated composable: it turns an `EyeBehavior` (Idle/Acquiring/Tracking/Lost/Frantic) plus a `reliefKey` into motion with `Animatable`s. Per-state motion loops live in `LaunchedEffect`s keyed on the motion kind, so leaving a state cancels them, and animated values are read only in the draw phase. `EyePreviews.kt` has deterministic previews of every state through the static `Eye`.
- `watch/` is the eye's personality as a pure-Kotlin state machine (**no Android imports, ever**). `ObsessiveWatcher.onPerception(p, nowMs)` / `onTick(nowMs)` → `WatcherState`, with two axes: `Sight` (SEEING/DARK/NO_PERMISSION/CAMERA_ERROR, with luma hysteresis) and `Watch` (Idle → Acquiring → Tracking → Lost). Frantic = not SEEING. Every threshold is in `WatcherConfig`. Time is always passed in, never read. `ObsessiveWatcherTest` covers the behavior spec, so change it together with the logic.
- `camera/` holds perception sources behind `FaceSource` (`fun perceptions(): Flow<Perception>`; collecting = perceiving, cancelling = releasing). `CameraFaceSource` is CameraX `ImageAnalysis` (front, ~640×480, KEEP_ONLY_LATEST) + ML Kit (FAST, classification, tracking), bound to a private lifecycle that lives exactly as long as the collection. It must be collected on the main thread. `FaceMapping.kt` holds the pure, tested pieces: `mapFace` (rotation swap + front-camera mirroring), `eyesOpen`, `meanLuma`. `FakeFaceSource` replays scripted `FakeScenarios` (pure Kotlin, also used by tests).
- `WatcherViewModel` owns the `ObsessiveWatcher`, merges perceptions with a 100 ms tick, and exposes `StateFlow<EyeUiState>` (an `EyeBehavior` for the eye, a `reliefKey`, and the raw `WatcherState` for debugging). `WhileSubscribed(0)` means the source runs only while the UI collects, so leaving the screen stops the camera. Dependencies are passed by hand through `WatcherViewModel.factory(...)`. Tests use `Clock { testScheduler.currentTime }` + a `StandardTestDispatcher` as Main + Turbine.
- `ui/WatcherScreen` renders the eye. Tap = ask for the camera again (`rememberCameraPermission`: asks once on first launch, re-checks on resume, opens Settings when permanently denied, never loops). Long-press = `DebugOverlay` (sight, watch, target, face boxes, luma, fps; debug builds can switch to a fake scenario there). `MainActivity` wires the ViewModel (camera source by default), runs immersive and keeps the screen on.
- **Merged manifest:** `AndroidManifest.xml` strips `INTERNET`/`ACCESS_NETWORK_STATE` that ML Kit's telemetry adds. After dependency changes, check `app/build/intermediates/merged_manifests/*/process*Manifest/AndroidManifest.xml`.

**Git:** local commits only. Never `git push` or touch remotes (also enforced in `.claude/settings.json`).

## Commands

Run these from the repo root with the Gradle wrapper:

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # build and install on a connected device/emulator
./gradlew lint                          # Android lint
./gradlew assembleDebug testDebugUnitTest lint   # the per-milestone gate
./gradlew test                          # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest          # instrumented tests (app/src/androidTest), needs a device

# Single test class / method
./gradlew :app:testDebugUnitTest --tests "com.salg.myeye.ui.eye.EyeGeometryTest"
./gradlew :app:testDebugUnitTest --tests "com.salg.myeye.ui.eye.EyeGeometryTest.highlights*"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.TestClass>
```

## Build setup notes

- **AGP 9.x with built-in Kotlin.** Only `com.android.application` and `org.jetbrains.kotlin.plugin.compose` are applied. Don't add `org.jetbrains.kotlin.android`, because AGP 9 supplies Kotlin support itself.
- `compileSdk` uses the new AGP DSL (`compileSdk { version = release(37) }`). `minSdk` is 31, compile/target SDK is 37, and Java/JVM target is 11. AGP 9.4 needs Gradle ≥ 9.6 (the wrapper is on 9.8.0).
- Declare every dependency and plugin version in `gradle/libs.versions.toml` and reference it through `libs.*` aliases. Compose library versions come from the Compose BOM, so Compose entries in the catalog have no version.
- `settings.gradle.kts` sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so repositories can only be declared there, never in module build files.
- The Compose theme lives in `ui/theme/` (`MyEyeTheme`, always dark, no dynamic color). Wrap new screens and `@Preview`s in `MyEyeTheme`.
