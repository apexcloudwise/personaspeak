package biz.pixelperfectstudios.personaspeak.ime

import android.content.Context
import android.os.Build
import biz.pixelperfectstudios.personaspeak.data.DataStoreProviderConfigStore
import biz.pixelperfectstudios.personaspeak.ui.brain.ProviderConfigStore

/**
 * Factory for wiring the production ProviderConfigStore instance.
 */
object PersonaSpeakBrain {
    fun createStore(context: Context): ProviderConfigStore =
        DataStoreProviderConfigStore.create(
            context = context.applicationContext,
            sdkInt = Build.VERSION.SDK_INT,
        )
}
