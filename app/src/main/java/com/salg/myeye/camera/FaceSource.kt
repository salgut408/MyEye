package com.salg.myeye.camera

import com.salg.myeye.watch.Perception
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where the eye's perceptions come from. Collecting the flow starts perceiving; cancelling the
 * collection stops it and releases whatever it holds (camera, detector).
 *
 * @param lowPower while true (the eye is asleep), perceive only about once per
 *   [LOW_POWER_INTERVAL_MS] to save battery. It can change at any time during collection.
 */
fun interface FaceSource {
    fun perceptions(lowPower: StateFlow<Boolean>): Flow<Perception>

    companion object {
        const val LOW_POWER_INTERVAL_MS = 1_000L
    }
}
