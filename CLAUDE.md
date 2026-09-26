# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

"My Eye" (`com.salg.myeye`) is **The Watcher**: a single-screen art app where a cartoon eye follows the viewer using the front camera and on-device ML Kit face detection. See `README.md` for the concept and `DECISIONS.md` for the defaults chosen along the way (add to it when you pick a new default).

It is a single `:app` module with a single Activity and Compose only. There is no DI framework (constructor injection by hand), no navigation, and no network. **Privacy is a hard requirement:** never log, save, or upload frames or face data, and the merged manifest must contain only `CAMERA`, never `INTERNET`.

Right now only the baseline exists: a near-black always-dark `MyEyeTheme`, portrait lock, and edge-to-edge. Keep this section updated as the architecture lands.

**Git:** local commits only. Never `git push` or touch remotes (also enforced in `.claude/settings.json`).

## Commands

Run these from the repo root with the Gradle wrapper:

```bash
./gradlew assembleDebug                 # build debug APK
./gradlew installDebug                  # build and install on a connected device/emulator
./gradlew lint                          # Android lint
./gradlew test                          # JVM unit tests (app/src/test)
./gradlew connectedAndroidTest          # instrumented tests (app/src/androidTest), needs a device

# Single test class / method
./gradlew :app:testDebugUnitTest --tests "com.salg.myeye.ExampleUnitTest"
./gradlew :app:testDebugUnitTest --tests "com.salg.myeye.ExampleUnitTest.addition_isCorrect"
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.salg.myeye.ExampleInstrumentedTest
```

## Build setup notes

- **AGP 9.x with built-in Kotlin.** Only `com.android.application` and `org.jetbrains.kotlin.plugin.compose` are applied. Don't add `org.jetbrains.kotlin.android`, because AGP 9 supplies Kotlin support itself.
- `compileSdk` uses the new AGP DSL (`compileSdk { version = release(36) { minorApiLevel = 1 } }`). `minSdk` is 31, compile/target SDK is 37, and Java/JVM target is 11. AGP 9.4 needs Gradle ≥ 9.6 (the wrapper is on 9.8.0).
- Declare every dependency and plugin version in `gradle/libs.versions.toml` and reference it through `libs.*` aliases. Compose library versions come from the Compose BOM, so Compose entries in the catalog have no version.
- `settings.gradle.kts` sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so repositories can only be declared there, never in module build files.
- The Compose theme lives in `ui/theme/` (`MyEyeTheme`, always dark, no dynamic color). Wrap new screens and `@Preview`s in `MyEyeTheme`.
