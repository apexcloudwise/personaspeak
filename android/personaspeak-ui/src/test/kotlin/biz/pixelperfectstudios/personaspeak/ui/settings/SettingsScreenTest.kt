package biz.pixelperfectstudios.personaspeak.ui.settings

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.Persona
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PersonaProvenance
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val jeeves = ValidatedPersona(
        id = PersonaId.bundled("jeeves"),
        provenance = PersonaProvenance.bundled,
        content = Persona(
            name = "Jeeves",
            context = "the valet",
            speechPatterns = listOf("Impeccable valet etiquette"),
            sampleLines = listOf("Very good, sir."),
        ),
    )

    private val bachchan = ValidatedPersona(
        id = PersonaId.bundled("amitabh-bachchan"),
        provenance = PersonaProvenance.bundled,
        content = Persona(
            name = "Amitabh Bachchan",
            context = "legendary Indian cinema presence",
            speechPatterns = listOf("Baritone dramatic pauses"),
            realPerson = true,
            sampleLines = listOf("Rishte mein toh hum tumhare baap lagte hain."),
        ),
    )

    @Test
    fun `SettingsHomeScreen renders all required groups with 48dp touch floors`() {
        var navigatedToPersonas = false
        var askSettingsOpened = false

        val state = SettingsState(
            destination = SettingsDestination.Home,
            activePersonaId = jeeves.id,
            personas = listOf(jeeves, bachchan),
            defaultMood = Mood.Polite,
        )

        composeRule.setContent {
            SettingsHomeScreen(
                state = state,
                onNavigateToPersonas = { navigatedToPersonas = true },
                onSelectDefaultMood = {},
                onOpenAskSettings = { askSettingsOpened = true },
                onClearNotice = {},
            )
        }

        // Top bar
        composeRule.onNodeWithTag("personaspeak_settings_topbar").assertExists()

        // CHARACTERS group
        composeRule.onNodeWithText("CHARACTERS").assertExists()
        composeRule.onNodeWithTag("personaspeak_settings_characters_row")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("personaspeak_settings_mood_row")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("personaspeak_settings_review_row")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)

        // Init notice
        composeRule.onNodeWithTag("personaspeak_settings_init_notice").assertExists()

        // THE BRAIN group
        composeRule.onNodeWithText("THE BRAIN").assertExists()
        composeRule.onNodeWithTag("personaspeak_settings_provider_row")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_settings_cloud_provider_row")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)

        // TYPING group
        composeRule.onNodeWithText("TYPING").assertExists()
        composeRule.onNodeWithTag("personaspeak_settings_typing_row")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)

        // Navigation clicks
        composeRule.onNodeWithTag("personaspeak_settings_characters_row").performClick()
        assertTrue(navigatedToPersonas)

        composeRule.onNodeWithTag("personaspeak_settings_typing_row").performScrollTo().performClick()
        assertTrue(askSettingsOpened)
    }

    @Test
    fun `SettingsHomeScreen mood dialog allows selecting default mood`() {
        var selectedMood: Mood? = null

        val state = SettingsState(
            destination = SettingsDestination.Home,
            activePersonaId = jeeves.id,
            personas = listOf(jeeves, bachchan),
            defaultMood = Mood.Polite,
        )

        composeRule.setContent {
            SettingsHomeScreen(
                state = state,
                onNavigateToPersonas = {},
                onSelectDefaultMood = { selectedMood = it },
                onOpenAskSettings = {},
                onClearNotice = {},
            )
        }

        // Open mood dialog
        composeRule.onNodeWithTag("personaspeak_settings_mood_row").performClick()
        composeRule.onNodeWithTag("personaspeak_settings_mood_dialog").assertIsDisplayed()

        // Select Witty
        composeRule.onNodeWithText("Witty").performClick()
        assertEquals(Mood.Witty, selectedMood)
    }

    @Test
    fun `PersonaBrowserScreen renders character cards and triggers detail navigation`() {
        var selectedDetailId: PersonaId? = null
        var backed = false

        val state = SettingsState(
            destination = SettingsDestination.Personas,
            activePersonaId = jeeves.id,
            personas = listOf(jeeves, bachchan),
        )

        composeRule.setContent {
            PersonaBrowserScreen(
                state = state,
                onBack = { backed = true },
                onSelectPersonaDetail = { selectedDetailId = it },
            )
        }

        composeRule.onNodeWithTag("personaspeak_browser_back")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("personaspeak_character_item_bundled:jeeves")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("personaspeak_active_badge_bundled:jeeves", useUnmergedTree = true)
            .assertExists()

        composeRule.onNodeWithTag("personaspeak_character_item_bundled:amitabh-bachchan")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)

        composeRule.onNodeWithTag("personaspeak_real_person_badge", useUnmergedTree = true)
            .assertExists()

        composeRule.onNodeWithTag("personaspeak_character_item_bundled:amitabh-bachchan")
            .performScrollTo()
            .performClick()
        assertEquals(bachchan.id, selectedDetailId)

        composeRule.onNodeWithTag("personaspeak_browser_back").performScrollTo().performClick()
        assertTrue(backed)
    }

    @Test
    fun `PersonaDetailScreen displays dossier, guidance, and set active action`() {
        var activatedId: PersonaId? = null
        var backed = false

        composeRule.setContent {
            PersonaDetailScreen(
                persona = bachchan,
                isActive = false,
                notice = null,
                onBack = { backed = true },
                onSetActive = { activatedId = it },
            )
        }

        composeRule.onNodeWithTag("personaspeak_detail_back")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)

        composeRule.onAllNodesWithText("Amitabh Bachchan").assertCountEquals(2)
        composeRule.onNodeWithText("Baritone dramatic pauses").assertExists()
        composeRule.onNodeWithText("\"Rishte mein toh hum tumhare baap lagte hain.\"").assertExists()

        // Guidance card
        composeRule.onNodeWithTag("personaspeak_try_on_keyboard_card").assertExists()
        composeRule.onNodeWithText("Guidance only: Active input method is configured in system settings.").assertExists()

        // Set active button
        composeRule.onNodeWithTag("personaspeak_set_active_persona")
            .assertExists()
            .assertHeightIsAtLeast(48.dp)
            .performScrollTo()
            .performClick()

        assertEquals(bachchan.id, activatedId)

        composeRule.onNodeWithTag("personaspeak_detail_back").performScrollTo().performClick()
        assertTrue(backed)
    }

    @Test
    fun `SettingsScreen full routing integration`() {
        var currentDest: SettingsDestination = SettingsDestination.Home
        var activeId = jeeves.id
        var mood = Mood.Polite

        val state = SettingsState(
            destination = currentDest,
            activePersonaId = activeId,
            personas = listOf(jeeves, bachchan),
            defaultMood = mood,
        )

        composeRule.setContent {
            SettingsScreen(
                state = state,
                onNavigate = { currentDest = it },
                onBack = { currentDest = SettingsDestination.Home },
                onSelectPersona = { activeId = it },
                onSelectDefaultMood = { mood = it },
                onOpenAskSettings = {},
            )
        }

        composeRule.onNodeWithTag("personaspeak_settings_topbar").assertExists()
    }
}
