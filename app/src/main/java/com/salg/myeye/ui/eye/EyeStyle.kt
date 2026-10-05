package com.salg.myeye.ui.eye

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.salg.myeye.ui.theme.Highlight
import com.salg.myeye.ui.theme.IrisTeal
import com.salg.myeye.ui.theme.Outline
import com.salg.myeye.ui.theme.Sclera
import com.salg.myeye.ui.theme.Void

/** Look of the eye. Proportions are fractions so the eye scales with any canvas. */
@Immutable
data class EyeStyle(
    val sclera: Color = Sclera,
    val outline: Color = Outline,
    val iris: Color = IrisTeal,
    val pupil: Color = Color.Black,
    val highlight: Color = Highlight,
    /** Eyelid fill. Defaults to the background so lids read as the eye narrowing; set a skin tone to see them. */
    val lid: Color = Void,
    /** Sclera width as a fraction of canvas width. */
    val widthFraction: Float = 0.86f,
    /** Sclera width / height (> 1 = slightly wide oval). */
    val aspect: Float = 1.3f,
    /** Iris diameter as a fraction of sclera width. */
    val irisFraction: Float = 0.45f,
    /** Max iris travel as a fraction of the sclera radius. */
    val maxTravel: Float = 0.45f,
    /** Max foreshortening of the iris along its travel direction at full deflection. */
    val maxSquash: Float = 0.3f,
    /** Outline thickness as a fraction of the sclera horizontal radius. */
    val outlineFraction: Float = 0.05f,
)
