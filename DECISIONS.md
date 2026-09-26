# Decisions

Defaults chosen while building The Watcher that the original brief did not specify. Newest milestone at the bottom.

## Milestone 1: Baseline

- **Versions (checked against Google Maven / Maven Central on 2026-09-26):** AGP 9.4.1, Kotlin (compose compiler plugin) 2.4.20, Compose BOM 2026.09.00, lifecycle 2.11.0, activity-compose 1.13.0, core-ktx 1.19.1. The template's other versions were already current.
- **Gradle wrapper 9.4.1 → 9.8.0.** AGP 9.4.1 refuses to run on Gradle below 9.6.0, so I moved the wrapper to the latest stable release (checksum pinned) instead of holding AGP back.
- **compileSdk/targetSdk 36 → 37.** Android 17 (API 37) is a stable, non-preview platform in the local SDK, and the test device (Pixel 7 Pro) runs it. The brief said to raise these if a newer stable level is out. minSdk stays 31.
- **Kotlin version = compose compiler plugin version.** With AGP's built-in Kotlin, applying `org.jetbrains.kotlin.plugin.compose` at 2.4.20 also sets the Kotlin compiler version. There is no separate `kotlin-android` plugin.
- **Theme is always dark, with dynamic color removed.** The artwork needs a fixed near-black stage (`#0B0B0D`) no matter the wallpaper or system theme. The window background and splash background use the same color, so the app never flashes white on launch.
- **Portrait lock via `android:screenOrientation="portrait"`.** On large screens (sw ≥ 600dp) Android 16+ ignores orientation locks for apps targeting 36+. That's acceptable because the ViewModel owns all watcher state and survives rotation anyway.
- **`.idea/` files are committed as the template left them** (the template's own `.gitignore` already excludes the machine-specific ones).
