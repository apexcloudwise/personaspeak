package biz.pixelperfectstudios.personaspeak.data

/** Fixed-code logging: events only, never values. Keeps secrets out of logcat by construction. */
enum class StoreEvent {
    LOAD_UNCONFIGURED,
    LOAD_CONFIGURED,
    LOAD_RECOVERED_FROM_STAGING,
    LOAD_INVALID_CLEARED,
    LOAD_UNAVAILABLE,
    SAVE_STAGED,
    SAVE_COMMITTED,
    SAVE_SWAPPED,
    SAVE_FAILED,
    CLEAR_DONE,
}

fun interface StoreLog {
    fun event(event: StoreEvent)
}

/** Default logger: fixed strings to logcat, no interpolation possible. */
class LogcatStoreLog : StoreLog {
    override fun event(event: StoreEvent) {
        android.util.Log.i("ProviderConfigStore", event.name)
    }
}
