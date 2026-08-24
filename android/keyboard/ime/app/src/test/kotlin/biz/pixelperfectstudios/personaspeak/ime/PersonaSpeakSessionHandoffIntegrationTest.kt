package biz.pixelperfectstudios.personaspeak.ime

import android.content.Context
import android.view.inputmethod.EditorInfo
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.test.core.app.ApplicationProvider
import biz.pixelperfectstudios.personaspeak.ime.editor.EditorSessionState
import biz.pixelperfectstudios.personaspeak.ime.editor.InputConnectionEditorPort
import biz.pixelperfectstudios.personaspeak.ime.host.ImeViewTreeOwners
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.providers.FakeProvider
import biz.pixelperfectstudios.personaspeak.ui.personas.AssetPersonaDocumentSource
import biz.pixelperfectstudios.personaspeak.ui.personas.BundledPersonaRepository
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewriteCoordinator
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewritePanelState
import biz.pixelperfectstudios.personaspeak.ui.rewrite.RewritePanelViewModel
import biz.pixelperfectstudios.personaspeak.ui.settings.PersonaSpeakSessionState
import biz.pixelperfectstudios.personaspeak.ui.settings.SettingsViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration regression test proving non-persistent session state handoff between
 * the Settings surface (SettingsViewModel / PersonaSpeakSettingsActivity) and the
 * IME strip (PersonaSpeakComposition / RewritePanelViewModel).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PersonaSpeakSessionHandoffIntegrationTest {

    private val context get() = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val sessionState = PersonaSpeakSessionState.instance

    @Before
    @After
    fun resetSession() {
        sessionState.reset()
    }

    @Test
    fun `settings selections hand off to next IME strip initialization`() {
        val repo = BundledPersonaRepository(AssetPersonaDocumentSource(context.assets))

        // 1. Settings surface initializes and user selects Dr. Schultz + Witty mood
        val settingsVm = SettingsViewModel(
            personasRepo = repo,
            sessionState = sessionState,
        )
        val targetPersonaId = PersonaId.bundled("dr-schultz")
        val targetMood = Mood.Witty

        settingsVm.selectPersona(targetPersonaId)
        settingsVm.selectDefaultMood(targetMood)

        assertEquals(targetPersonaId, sessionState.activePersonaId)
        assertEquals(targetMood, sessionState.defaultMood)
        assertTrue(settingsVm.state.value.notice!!.contains("Takes effect on next keyboard initialization in this session"))

        // 2. Simulate next IME keyboard initialization (as in PersonaSpeakComposition)
        val owners = ImeViewTreeOwners()
        val editorSession = EditorSessionState()
        val editorPort = InputConnectionEditorPort(
            sessionState = editorSession,
            connectionSupplier = { null },
            editorInfoSupplier = { EditorInfo() },
        )
        val coordinator = RewriteCoordinator(
            personas = repo,
            editor = editorPort,
            provider = FakeProvider(),
        )

        val imeViewModel = ViewModelProvider(
            owners.viewModelStore,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(
                    modelClass: Class<T>,
                    extras: CreationExtras,
                ): T {
                    val session = PersonaSpeakSessionState.instance
                    return RewritePanelViewModel(
                        coordinator = coordinator,
                        personas = repo,
                        sessionState = session,
                        initialPersonaId = session.activePersonaId,
                        initialMood = session.defaultMood,
                        savedStateHandle = SavedStateHandle(),
                    ) as T
                }
            },
        )[RewritePanelViewModel::class.java]

        // 3. Assert IME strip initialized with the handoff selections from Settings
        val initialState = imeViewModel.state.value
        assertTrue(initialState is RewritePanelState.Resting)
        val resting = initialState as RewritePanelState.Resting
        assertEquals(targetPersonaId, resting.persona.id)
        assertEquals(targetMood, resting.mood)

        // 4. M4 Boundary check: assert zero storage / SharedPreferences / disk writes
        val personaPrefs = context.getSharedPreferences("personaspeak_settings", Context.MODE_PRIVATE)
        assertTrue("Zero disk persistence / SharedPreferences writes in M3", personaPrefs.all.isEmpty())
    }

    @Test
    fun `strip in-row selection updates session state for settings`() {
        val repo = BundledPersonaRepository(AssetPersonaDocumentSource(context.assets))
        val editorSession = EditorSessionState()
        val editorPort = InputConnectionEditorPort(
            sessionState = editorSession,
            connectionSupplier = { null },
            editorInfoSupplier = { EditorInfo() },
        )
        val coordinator = RewriteCoordinator(
            personas = repo,
            editor = editorPort,
            provider = FakeProvider(),
        )

        val imeViewModel = RewritePanelViewModel(
            coordinator = coordinator,
            personas = repo,
            sessionState = sessionState,
        )

        // User changes persona to Amitabh Bachchan on strip
        val bachchanId = PersonaId.bundled("amitabh-bachchan")
        imeViewModel.selectPersona(bachchanId)
        imeViewModel.selectMood(Mood.Formal)

        assertEquals(bachchanId, sessionState.activePersonaId)
        assertEquals(Mood.Formal, sessionState.defaultMood)

        // Opening Settings surface reflects the strip selection
        val settingsVm = SettingsViewModel(
            personasRepo = repo,
            sessionState = sessionState,
        )
        assertEquals(bachchanId, settingsVm.state.value.activePersonaId)
        assertEquals(Mood.Formal, settingsVm.state.value.defaultMood)

        // M4 Boundary check: assert zero storage writes
        val personaPrefs = context.getSharedPreferences("personaspeak_settings", Context.MODE_PRIVATE)
        assertTrue("Zero disk persistence / SharedPreferences writes in M3", personaPrefs.all.isEmpty())
    }
}
