# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project state

"My Eye" (`com.salg.myeye`) is a freshly generated Android Studio project: a single-module, single-Activity Jetpack Compose app. `MainActivity` still contains the template `Greeting` composable. There is no navigation, DI, networking, or persistence layer yet, so there are no architectural conventions to follow beyond the ones below. Update this file as real architecture gets added.

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
- `compileSdk` uses the new AGP DSL (`compileSdk { version = release(36) { minorApiLevel = 1 } }`). `minSdk` is 31, `targetSdk` is 36, and Java/JVM target is 11.
- Declare every dependency and plugin version in `gradle/libs.versions.toml` and reference it through `libs.*` aliases. Compose library versions come from the Compose BOM, so Compose entries in the catalog have no version.
- `settings.gradle.kts` sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS`, so repositories can only be declared there, never in module build files.
- The Compose theme lives in `ui/theme/` (`MyEyeTheme`, with dynamic color on Android 12+). Wrap new screens and `@Preview`s in `MyEyeTheme`.
