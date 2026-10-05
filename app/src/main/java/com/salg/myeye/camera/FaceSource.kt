package com.salg.myeye.camera

import com.salg.myeye.watch.Perception
import kotlinx.coroutines.flow.Flow

/**
 * Where the eye's perceptions come from. Collecting the flow starts perceiving; cancelling the
 * collection stops it and releases whatever it holds (camera, detector).
 */
fun interface FaceSource {
    fun perceptions(): Flow<Perception>
}
