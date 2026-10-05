# Decisions

Defaults chosen while building The Watcher that the original brief did not specify. Newest milestone at the bottom.

## Milestone 1: Baseline

- **Versions (checked against Google Maven / Maven Central on 2026-09-26):** AGP 9.4.1, Kotlin (compose compiler plugin) 2.4.20, Compose BOM 2026.09.00, lifecycle 2.11.0, activity-compose 1.13.0, core-ktx 1.19.1. The template's other versions were already current.
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
