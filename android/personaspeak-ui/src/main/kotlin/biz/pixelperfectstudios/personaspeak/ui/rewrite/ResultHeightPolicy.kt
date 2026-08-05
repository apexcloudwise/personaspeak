package biz.pixelperfectstudios.personaspeak.ui.rewrite

import kotlin.math.roundToInt

/**
 * Maximum height, in pixels, of the Review candidate body.
 *
 * The dedicated row sits above ASK's own candidate strip and key rows, and the
 * whole point of the row is that it never covers them. So the Review body is
 * bounded twice: it takes at most 40% of the IME container height sampled
 * *before* Review expanded it, and never more than 320dp regardless of how
 * tall the container is. The 40% term keeps small screens usable; the 320dp
 * term keeps large ones from handing the row half the display.
 *
 * The caller samples the height once per candidate and freezes it. Sampling
 * after expansion would feed the row's own growth back into its bound.
 *
 * Returns 0 for a sample or density that cannot produce a meaningful bound —
 * the caller treats 0 as "no cap computed yet" rather than "zero height".
 */
internal fun resultBodyMaxHeightPx(
    preExpansionHeightPx: Int,
    density: Float,
): Int {
    if (preExpansionHeightPx <= 0 || density <= 0f) return 0
    val fortyPercent = (preExpansionHeightPx * 0.4f).roundToInt()
    val hardCap = (320f * density).roundToInt()
    return minOf(fortyPercent, hardCap)
}
