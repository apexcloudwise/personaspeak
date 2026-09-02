/*
 * PersonaSpeak — settings activity for the FlorisBoard fork.
 *
 * Ported from the ASK host's PersonaSpeakSettingsActivity; the only
 * behavioral difference is the host-keyboard-settings entry point, which
 * opens FlorisBoard's own settings activity instead of ASK's.
 *
 * Licensed under the Apache License, Version 2.0; this file follows the
 * PersonaSpeak module law (first-party code in clearly separated packages).
 */
package biz.pixelperfectstudios.personaspeak.floris

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import biz.pixelperfectstudios.personaspeak.ime.PersonaSpeakBrain
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.providers.OpenRouterModels
import biz.pixelperfectstudios.personaspeak.ui.personas.AssetPersonaDocumentSource
import biz.pixelperfectstudios.personaspeak.ui.personas.BundledPersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.settings.ModelInfo
import biz.pixelperfectstudios.personaspeak.ui.settings.SettingsDestination
import biz.pixelperfectstudios.personaspeak.ui.settings.SettingsScreen
import biz.pixelperfectstudios.personaspeak.ui.settings.SettingsViewModel
import dev.patrickgold.florisboard.app.FlorisAppActivity

/**
 * Activity hosting the first-party PersonaSpeak Settings surface.
 *
 * Plumbed to receive intent extras for deep-linking into the Home, Personas library,
 * or Provider overview sections.
 */
class FlorisPersonaSpeakSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val destinationExtra = intent?.getStringExtra(EXTRA_DESTINATION)
        val personaIdExtra = intent?.getStringExtra(EXTRA_PERSONA_ID)

        val initialDestination: SettingsDestination = when (destinationExtra) {
            DESTINATION_PERSONAS -> SettingsDestination.Personas
            DESTINATION_PROVIDERS -> SettingsDestination.ProviderSetup
            DESTINATION_PERSONA_DETAIL -> {
                if (personaIdExtra != null) {
                    SettingsDestination.PersonaDetail(PersonaId(personaIdExtra))
                } else {
                    SettingsDestination.Personas
                }
            }
            else -> SettingsDestination.Home
        }

        val personaRepo = BundledPersonaRepository(AssetPersonaDocumentSource(assets))

        setContent {
            val providerStore = remember {
                PersonaSpeakBrain.createStore(applicationContext)
            }
            val viewModel = remember {
                SettingsViewModel(
                    personasRepo = personaRepo,
                    initialDestination = initialDestination,
                    providerConfigStore = providerStore,
                )
            }
            val state by viewModel.state.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.refreshProviderStatus()
            }

            BackHandler(enabled = state.destination !is SettingsDestination.Home) {
                when (state.destination) {
                    is SettingsDestination.PersonaDetail -> viewModel.navigateTo(SettingsDestination.Personas)
                    is SettingsDestination.Personas -> viewModel.navigateTo(SettingsDestination.Home)
                    is SettingsDestination.ProviderSetup -> viewModel.navigateTo(SettingsDestination.Home)
                    else -> finish()
                }
            }

            SettingsScreen(
                state = state,
                onNavigate = viewModel::navigateTo,
                onBack = {
                    when (state.destination) {
                        is SettingsDestination.PersonaDetail -> viewModel.navigateTo(SettingsDestination.Personas)
                        is SettingsDestination.Personas -> viewModel.navigateTo(SettingsDestination.Home)
                        is SettingsDestination.ProviderSetup -> viewModel.navigateTo(SettingsDestination.Home)
                        else -> finish()
                    }
                },
                onSelectPersona = viewModel::selectPersona,
                onSelectDefaultMood = viewModel::selectDefaultMood,
                onOpenAskSettings = {
                    val hostIntent = Intent(this, FlorisAppActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(hostIntent)
                },
                onOpenEnableIme = {
                    val imeIntent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(imeIntent)
                },
                onSaveProvider = viewModel::saveProvider,
                onClearProvider = viewModel::clearProvider,
                onFetchModels = {
                    OpenRouterModels.fetch().map { models ->
                        models.map { ModelInfo(it.id, it.name, it.isFree) }
                    }
                },
                onClearNotice = viewModel::clearNotice,
            )
        }
    }

    companion object {
        const val EXTRA_DESTINATION = "biz.pixelperfectstudios.personaspeak.extra.DESTINATION"
        const val EXTRA_PERSONA_ID = "biz.pixelperfectstudios.personaspeak.extra.PERSONA_ID"

        const val DESTINATION_HOME = "home"
        const val DESTINATION_PERSONAS = "personas"
        const val DESTINATION_PERSONA_DETAIL = "persona_detail"
        const val DESTINATION_PROVIDERS = "providers"

        fun createIntent(
            context: Context,
            destination: String = DESTINATION_HOME,
            personaId: String? = null,
        ): Intent {
            return Intent(context, FlorisPersonaSpeakSettingsActivity::class.java).apply {
                putExtra(EXTRA_DESTINATION, destination)
                if (personaId != null) {
                    putExtra(EXTRA_PERSONA_ID, personaId)
                }
            }
        }
    }
}
