# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

"My Eye" (`com.salg.myeye`) is **The Watcher**: a single-screen art app where a cartoon eye follows the viewer using the front camera and on-device ML Kit face detection. See `README.md` for the concept and `DECISIONS.md` for the defaults chosen along the way (add to it when you pick a new default).

It has a single Activity and is Compose only, split into Gradle modules (below). There is no DI framework (constructor injection by hand), no navigation, and no network. **Privacy is a hard requirement:** never log, save, or upload frames or face data, and the merged manifest must contain only `CAMERA`, never `INTERNET`.

## Modules

```
:app           MainActivity, WatcherViewModel, ui/WatcherScreen, DebugOverlay, CameraPermission
 ├─► :feature:eye   Android lib + Compose  (ui/eye)    → :core:ui
 ├─► :core:camera   Android lib            (camera)    → api :core:watch
 ├─► :core:ui       Android lib + Compose  (ui/theme)
 └─► :core:watch    pure Kotlin/JVM        (watch)     no deps
build-logic/   convention plugins: myeye.android.application / .android.library / .android.compose / .jvm.library
```

- Source lives in `<module>/src/main/kotlin/com/salg/myeye/<package>/` (`:app` keeps `src/main/java`). **Packages didn't change when modules were split**, so a class's package tells you its module.
- Dependencies point one way only (down the tree). `:core:watch` cannot see Android, and `:feature:eye` can't see the camera or the state machine; the build enforces both. Don't add an upward or sideways dependency to get around that. Move the code or pass data in instead.
- Add a library dependency to the module that uses it (CameraX and ML Kit live only in `:core:camera`). Shared build settings go in the convention plugins, not copied into module files.

Architecture (keep this updated when it changes):

- `:feature:eye` (`ui/eye/`) is the eye, in three layers. `EyeGeometry.kt` is pure, JVM-tested math (sizes, iris travel/foreshortening, lid positions, fixed highlights). `EyeRenderer` is a swappable `DrawScope` renderer (`CartoonEyeRenderer`) that's a pure function of `(gaze, EyeExpression, EyeStyle)`. `LivingEye` is the animated composable: it turns an `EyeBehavior` (Idle/Acquiring/Tracking/Lost/Frantic) plus one-shot `reliefKey`/`blinkKey`/`wakeKey` counters into motion with `Animatable`s (also idle breathing/hippus, frantic strain). Per-state motion loops live in `LaunchedEffect`s keyed on the motion kind, so leaving a state cancels them, and animated values are read only in the draw phase. `EyePreviews.kt` has deterministic previews of every state through the static `Eye`.
- `:core:watch` (`watch/`) is the eye's personality as a pure-Kotlin state machine (**no Android imports, ever**). `ObsessiveWatcher.onPerception(p, nowMs)` / `onTick(nowMs)` → `WatcherState`, with two axes: `Sight` (SEEING/DARK/NO_PERMISSION/CAMERA_ERROR, with luma hysteresis) and `Watch` (Idle → Acquiring → Tracking → Lost). Frantic = not SEEING. A third axis, `Alertness` (AWAKE/DROWSY/ASLEEP), sits on top: alone for `sleepAfterMs` or at max panic for `exhaustAfterMs` it sleeps, ignoring everything until any face wakes it. One-shot events (`relief`, `mirrorBlinkStart`, `wake`) are true for exactly one state, and the ViewModel turns them into counters. Every threshold is in `WatcherConfig` (the README has a tuning table). Time is always passed in, never read. `ObsessiveWatcherTest` covers the behavior spec, so change it together with the logic.
- `:core:camera` (`camera/`) holds perception sources behind `FaceSource` (`fun perceptions(lowPower: StateFlow<Boolean>): Flow<Perception>`; collecting = perceiving, cancelling = releasing; while `lowPower` (asleep) perceive only ~1 frame/s). `CameraFaceSource` is CameraX `ImageAnalysis` (front, ~640×480, KEEP_ONLY_LATEST) + ML Kit (FAST, classification, tracking), bound to a private lifecycle that lives exactly as long as the collection. It must be collected on the main thread. `FaceMapping.kt` holds the pure, tested pieces: `mapFace` (rotation swap + front-camera mirroring), `eyesOpen`, `meanLuma`. `FakeFaceSource` replays scripted `FakeScenarios` (pure Kotlin, also used by tests).
- `:app`: `WatcherViewModel` owns the `ObsessiveWatcher`, merges perceptions with a 100 ms tick, and exposes `StateFlow<EyeUiState>` (an `EyeBehavior` for the eye, a `reliefKey`, and the raw `WatcherState` for debugging). `WhileSubscribed(0)` means the source runs only while the UI collects, so leaving the screen stops the camera. Dependencies are passed by hand through `WatcherViewModel.factory(...)`. Tests use `Clock { testScheduler.currentTime }` + a `StandardTestDispatcher` as Main + Turbine. The tick loop never ends, so use `advanceTimeBy`, never `advanceUntilIdle`.
- `ui/WatcherScreen` renders the eye. Tap = ask for the camera again (`rememberCameraPermission`: asks once on first launch, re-checks on resume, opens Settings when permanently denied, never loops). Long-press = `DebugOverlay` (sight, watch, target, face boxes, luma, fps; debug builds can switch to a fake scenario there). `MainActivity` wires the ViewModel (camera source by default), runs immersive and keeps the screen on.
- **Merged manifest:** `app/src/main/AndroidManifest.xml` declares `CAMERA` and strips `INTERNET`/`ACCESS_NETWORK_STATE` that ML Kit's telemetry adds. After dependency changes, check `app/build/intermediates/merged_manifests/*/process*Manifest/AndroidManifest.xml`.

**Git:** local commits only. Never `git push` or touch remotes (also enforced in the local, untracked `.claude/settings.json`). Commit messages are plain: no "Co-Authored-By: Claude" or "Generated with Claude" lines. Commits use the repo-local personal identity, never the global work email.

## Commands

Run these from the repo root with the Gradle wrapper:

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # build and install on a connected device/emulator
./gradlew lint                          # Android lint
./gradlew assembleDebug test lint      # the gate: every module's build, unit tests and lint
./gradlew test                          # unit tests in all modules
./gradlew connectedAndroidTest          # instrumented tests (app/src/androidTest), needs a device

# One module's tests (JVM module uses `test`, Android modules `testDebugUnitTest`)
./gradlew :core:watch:test
./gradlew :core:camera:testDebugUnitTest
./gradlew :feature:eye:testDebugUnitTest
./gradlew :app:testDebugUnitTest

# Single test class / method
./gradlew :core:watch:test --tests "com.salg.myeye.watch.ObsessiveWatcherTest"
./gradlew :feature:eye:testDebugUnitTest --tests "com.salg.myeye.ui.eye.EyeGeometryTest.highlights*"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=<fully.qualified.TestClass>
```

## Build setup notes

- **Convention plugins (`build-logic/`, an included build).** Module build files apply `myeye.*` plugins from the catalog (`alias(libs.plugins.myeye.android.library)` etc.). These set SDK levels, Java 11 and Compose. SDK levels are `compileSdk`/`targetSdk`/`minSdk` entries in `gradle/libs.versions.toml`. The root `build.gradle.kts` declares every real plugin with `apply false` so all modules share one classpath; build-logic only depends on them `compileOnly`.
- **AGP 9.x with built-in Kotlin.** Android modules get Kotlin from AGP itself; never add `org.jetbrains.kotlin.android`. Only the pure JVM module (`:core:watch`) applies `org.jetbrains.kotlin.jvm`. Kotlin's JVM target follows `compileOptions` (Java 11).
- `compileSdk` uses the new AGP DSL (`compileSdk { version = release(…) }`, set in `ProjectExtensions.kt`). `minSdk` is 31, compile/target SDK is 37, and Java/JVM target is 11. **AGP is pinned to 9.2.1 to match the installed Android Studio (2025.3.4).** Studio won't sync a project with a newer AGP than it bundles, even though `./gradlew` builds fine, so don't bump AGP unless Studio is updated first. The Gradle wrapper is on 9.8.0.
- Declare every dependency and plugin version in `gradle/libs.versions.toml` and reference it through `libs.*` aliases. Compose library versions come from the Compose BOM, so Compose entries in the catalog have no version.
- `settings.gradle.kts` sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so repositories can only be declared there, never in module build files.
- The Compose theme lives in `:core:ui` (`ui/theme/`: `MyEyeTheme`, always dark, no dynamic color). Wrap new screens and `@Preview`s in `MyEyeTheme`.
