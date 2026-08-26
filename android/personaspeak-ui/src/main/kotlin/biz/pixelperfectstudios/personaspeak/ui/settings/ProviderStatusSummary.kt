package biz.pixelperfectstudios.personaspeak.ui.settings

/**
 * Connection status of the configured brain. Mirrors the store outcome minus
 * the secret: nothing here may carry credential material.
 */
sealed interface ProviderStatusSummary {
    /** No provider configured; rewrites fall back to the offline fake. */
    data object Unconfigured : ProviderStatusSummary

    /** A provider id and its configuration timestamp. */
    data class Configured(
        val providerId: String,
        val configuredAtEpochMs: Long,
    ) : ProviderStatusSummary

    /** Storage or Keystore is currently broken. */
    data object Unavailable : ProviderStatusSummary

    /** Stored state was unreadable and has been cleared; reconfigure. */
    data object InvalidCredentials : ProviderStatusSummary
}
