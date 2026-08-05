package biz.pixelperfectstudios.personaspeak.ui.rewrite

import kotlin.test.assertEquals
import org.junit.Test

class ResultHeightPolicyTest {

    @Test
    fun `uses forty percent below hard cap`() {
        assertEquals(400, resultBodyMaxHeightPx(1000, density = 2f))
    }

    @Test
    fun `caps body at 320dp`() {
        assertEquals(640, resultBodyMaxHeightPx(2400, density = 2f))
    }

    @Test
    fun `returns zero for an invalid pre-expansion sample`() {
        assertEquals(0, resultBodyMaxHeightPx(0, density = 2f))
    }

    @Test
    fun `returns zero for a negative pre-expansion sample`() {
        assertEquals(0, resultBodyMaxHeightPx(-1, density = 2f))
    }

    @Test
    fun `returns zero for an invalid density`() {
        assertEquals(0, resultBodyMaxHeightPx(1000, density = 0f))
    }
}
