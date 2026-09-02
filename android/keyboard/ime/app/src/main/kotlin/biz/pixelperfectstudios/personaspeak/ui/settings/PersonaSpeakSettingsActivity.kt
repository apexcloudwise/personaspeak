package biz.pixelperfectstudios.personaspeak.ui.settings

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
import androidx.core.app.NotificationManagerCompat
import biz.pixelperfectstudios.personaspeak.ime.PersonaSpeakBrain
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.providers.OpenRouterModels
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
            DESTINATION_PROVIDERS -> SettingsDestination.ProviderSetup
            DESTINATION_SUGGESTED_REPLIES -> SettingsDestination.SuggestedReplies
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
                    notificationAccessProbe = {
                        NotificationManagerCompat.getEnabledListenerPackages(this)
                            .contains(packageName)
                    },
                )
            }
            val state by viewModel.state.collectAsState()

            LaunchedEffect(Unit) {
                viewModel.refreshProviderStatus()
                viewModel.refreshSuggestedRepliesStatus()
            }

            // Returning from the system notification-access screen (onResume,
            // destination unchanged) must refresh the live status too.
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                        viewModel.refreshSuggestedRepliesStatus()
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            BackHandler(enabled = state.destination !is SettingsDestination.Home) {
                when (state.destination) {
                    is SettingsDestination.PersonaDetail -> viewModel.navigateTo(SettingsDestination.Personas)
                    is SettingsDestination.Personas -> viewModel.navigateTo(SettingsDestination.Home)
                    is SettingsDestination.ProviderSetup -> viewModel.navigateTo(SettingsDestination.Home)
                    is SettingsDestination.SuggestedReplies -> viewModel.navigateTo(SettingsDestination.Home)
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
                    val askIntent = Intent(this, MainSettingsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    startActivity(askIntent)
                },
                onOpenNotificationAccessSettings = {
                    // Reached only through the consent gate on SuggestedRepliesScreen.
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    })
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
        const val DESTINATION_SUGGESTED_REPLIES = "suggested_replies"

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
