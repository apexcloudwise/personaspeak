package biz.pixelperfectstudios.personaspeak.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.ui.personas.AssetPersonaDocumentSource
import biz.pixelperfectstudios.personaspeak.ui.personas.BundledPersonaRepository
import com.anysoftkeyboard.ui.settings.MainSettingsActivity

/**
 * Activity hosting the first-party PersonaSpeak Settings surface.
 *
 * Plumbed to receive intent extras for deep-linking into the Home, Personas library,
 * or Provider overview sections.
 */
class PersonaSpeakSettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val destinationExtra = intent?.getStringExtra(EXTRA_DESTINATION)
        val personaIdExtra = intent?.getStringExtra(EXTRA_PERSONA_ID)

        val initialDestination: SettingsDestination = when (destinationExtra) {
            DESTINATION_PERSONAS -> SettingsDestination.Personas
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
            val viewModel = remember {
                SettingsViewModel(
                    personasRepo = personaRepo,
                    initialDestination = initialDestination,
                )
            }
            val state by viewModel.state.collectAsState()

            BackHandler(enabled = state.destination !is SettingsDestination.Home) {
                when (state.destination) {
                    is SettingsDestination.PersonaDetail -> viewModel.navigateTo(SettingsDestination.Personas)
                    is SettingsDestination.Personas -> viewModel.navigateTo(SettingsDestination.Home)
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
                        else -> finish()
                    }
                },
                onSelectPersona = viewModel::selectPersona,
                onSelectDefaultMood = viewModel::selectDefaultMood,
                onOpenAskSettings = {
                    val askIntent = Intent(this, MainSettingsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(askIntent)
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
            return Intent(context, PersonaSpeakSettingsActivity::class.java).apply {
                putExtra(EXTRA_DESTINATION, destination)
                if (personaId != null) {
                    putExtra(EXTRA_PERSONA_ID, personaId)
                }
            }
        }
    }
}
