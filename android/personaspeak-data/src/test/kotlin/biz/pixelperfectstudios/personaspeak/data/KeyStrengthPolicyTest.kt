package biz.pixelperfectstudios.personaspeak.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pins the StrongBox API floor: 26/27 never request it, 28+ do. */
class KeyStrengthPolicyTest {

    @Test
    fun `API 26 does not request StrongBox`() {
        assertFalse(KeyStrengthPolicy.requestStrongBox(26))
    }

    @Test
    fun `API 27 does not request StrongBox`() {
        assertFalse(KeyStrengthPolicy.requestStrongBox(27))
    }

    @Test
    fun `API 28 requests StrongBox`() {
        assertTrue(KeyStrengthPolicy.requestStrongBox(28))
    }

    @Test
    fun `API 35 requests StrongBox`() {
        assertTrue(KeyStrengthPolicy.requestStrongBox(35))
    }
}
