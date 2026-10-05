package com.salg.myeye.watch

/** Every tunable threshold of the watcher's personality, in one place. See README "Tuning". */
data class WatcherConfig(
    /** Mean luma (0–255) below which the eye starts going blind… */
    val darkEnterLuma: Int = 30,
    /** …for at least this long. */
    val darkAfterMs: Long = 1_000,
    /** Once dark, it only sees again above this luma (hysteresis). */
    val darkExitLuma: Int = 45,

    /** A candidate must stay in view this long before the eye commits to it. */
    val acquireMs: Long = 400,
    /** After being blind it's desperate and commits much faster. */
    val reliefAcquireMs: Long = 150,
    /** How long after regaining sight a new face still counts as "relief". */
    val reliefWindowMs: Long = 5_000,

    /** How long the eye waits for its person after losing them. */
    val lostTimeoutMs: Long = 4_000,
    /** While lost, any face this close (normalized distance) to the last position is taken as them. */
    val reacquireRadius: Float = 0.35f,
    /** If they left past this |x|, the eye keeps looking after them… */
    val lookAfterEdge: Float = 0.6f,
    /** …pushed this much further toward that edge. */
    val lookAfterPush: Float = 0.25f,

    /** Blind for this long = maximum panic. */
    val franticRampMs: Long = 10_000,

    /** Tracked face's eyes-open probability below which the eye blinks with them. */
    val mirrorBlinkBelow: Float = 0.3f,

    /** Face sizes mapped to closeness 0 (far) … 1 (close); closeness dilates the pupil. */
    val farSize: Float = 0.12f,
    val nearSize: Float = 0.45f,
)
