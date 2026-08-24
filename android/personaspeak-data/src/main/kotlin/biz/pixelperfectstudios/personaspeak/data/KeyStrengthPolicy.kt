package biz.pixelperfectstudios.personaspeak.data

/**
 * SDK-gated key-strength decisions for the credential key.
 *
 * StrongBox exists from API 28; requesting it on 26/27 is an unguarded-API
 * bug, so the gate lives here where both branches are unit-testable without
 * hardware. The cipher applies [requestStrongBox] inside its own fallback
 * try/catch for devices where API >= 28 but no StrongBox chip answers.
 */
object KeyStrengthPolicy {
    const val STRONG_BOX_MIN_SDK: Int = 28

    /** True only when the platform can legally accept a StrongBox request. */
    fun requestStrongBox(sdkInt: Int): Boolean = sdkInt >= STRONG_BOX_MIN_SDK
}
