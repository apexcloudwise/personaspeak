package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.Persona
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PersonaProvenance
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSessionToken
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSnapshot
import biz.pixelperfectstudios.personaspeak.ui.editor.RequestGeneration
import biz.pixelperfectstudios.personaspeak.ui.editor.Utf16Selection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dedicated row is the only surface PersonaSpeak owns. These tests pin the
 * geometry and state contracts: every interactive control clears the 48dp touch
 * minimum, all states render accurately, and the Review body never grows past
 * the frozen min(320dp, 40% of the pre-expansion IME height) cap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RewritePanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testPersona = ValidatedPersona(
        id = PersonaId.bundled("jeeves"),
        provenance = PersonaProvenance.bundled,
        content = Persona(
            name = "Jeeves",
            context = " (the valet)",
            speechPatterns = listOf("Formal"),
        ),
    )

    private val candidate = RewriteCandidate(
        snapshot = EditorSnapshot(
            session = EditorSessionToken(1L),
            generation = RequestGeneration(1L),
            draft = "synthetic draft",
            selection = Utf16Selection(0, 15),
        ),
        replacement = "synthetic replacement",
    )

    private val longCandidate = candidate.copy(
        replacement = (1..80).joinToString("\n") { "synthetic-line-$it" },
    )

    private fun setPanel(
        state: RewritePanelState,
        preExpansionImeHeightPx: Int = 1000,
        onDismiss: () -> Unit = {},
        onRewrite: () -> Unit = {},
        onApply: () -> Unit = {},
        onOpenPersonaPicker: () -> Unit = {},
        onOpenMoodPicker: () -> Unit = {},
    ) {
        composeRule.setContent {
            RewritePanel(
                state = state,
                onRewrite = onRewrite,
                onApply = onApply,
                onDismiss = onDismiss,
                onSettings = {},
                preExpansionImeHeightPx = { preExpansionImeHeightPx },
                onOpenPersonaPicker = onOpenPersonaPicker,
                onOpenMoodPicker = onOpenMoodPicker,
            )
        }
    }

    @Test
    fun `resting exposes 48dp persona chip, mood chip, rewrite, and settings`() {
        setPanel(RewritePanelState.Resting(testPersona, Mood.Polite))

        composeRule.onNodeWithTag("personaspeak_persona_chip")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_mood_chip")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_rewrite")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_settings")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `persona picker displays character tiles and 48dp targets`() {
        setPanel(
            RewritePanelState.PersonaPicker(
                personas = listOf(testPersona),
                selectedId = testPersona.id,
                currentMood = Mood.Polite,
            ),
        )

        composeRule.onNodeWithTag("personaspeak_persona_picker").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_persona_tile_jeeves")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_picker_close")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_browse_all")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `mood picker displays mood options and 48dp targets`() {
        setPanel(
            RewritePanelState.MoodPicker(
                moods = Mood.ALL,
                selectedMood = Mood.Polite,
                currentPersona = testPersona,
            ),
        )

        composeRule.onNodeWithTag("personaspeak_mood_picker").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_mood_tile_polite")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_mood_tile_witty")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_mood_picker_close")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `loading exposes progress, 48dp cancel control, and settings`() {
        setPanel(RewritePanelState.Loading(testPersona, Mood.Polite))

        composeRule.onNodeWithTag("personaspeak_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_cancel")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_settings")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `loading cancel invokes the dismiss callback`() {
        var dismissCount = 0
        setPanel(RewritePanelState.Loading(testPersona, Mood.Polite), onDismiss = { dismissCount++ })

        composeRule.onNodeWithTag("personaspeak_cancel").performClick()

        assertEquals(1, dismissCount)
    }

    @Test
    fun `review displays candidate and 48dp apply, again, dismiss controls`() {
        setPanel(RewritePanelState.Review(testPersona, Mood.Polite, candidate))

        composeRule.onNodeWithTag("personaspeak_candidate").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_apply")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_again")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_dismiss")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_settings")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `applied verified state renders badge and 48dp dismiss`() {
        setPanel(RewritePanelState.AppliedVerified(testPersona, Mood.Polite, candidate))

        composeRule.onNodeWithTag("personaspeak_applied_verified").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_dismiss")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `write unconfirmed error state exposes dismiss only - no retry, use this, or again affordances`() {
        setPanel(
            RewritePanelState.Error(
                error = StitchError.WriteUnconfirmed,
                persona = testPersona,
                mood = Mood.Polite,
            ),
        )

        composeRule.onNodeWithTag("personaspeak_error_card").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_message").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_dismiss")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)

        // Verify strictly dismiss only: no retry, no apply, no again buttons
        composeRule.onNodeWithTag("personaspeak_retry").assertDoesNotExist()
        composeRule.onNodeWithTag("personaspeak_apply").assertDoesNotExist()
        composeRule.onNodeWithTag("personaspeak_again").assertDoesNotExist()
    }

    @Test
    fun `stale editor error state exposes retry and dismiss`() {
        setPanel(
            RewritePanelState.Error(
                error = StitchError.StaleEditor,
                persona = testPersona,
                mood = Mood.Polite,
            ),
        )

        composeRule.onNodeWithTag("personaspeak_error_card").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_message").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_retry")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_dismiss")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `offline error state renders amber card with message and 48dp actions`() {
        setPanel(
            RewritePanelState.Error(
                error = StitchError.Offline,
                persona = testPersona,
                mood = Mood.Polite,
            ),
        )

        composeRule.onNodeWithTag("personaspeak_error_card").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_message").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_retry")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_dismiss")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_settings")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `review body honours forty percent cap of pre-expansion sample`() {
        setPanel(RewritePanelState.Review(testPersona, Mood.Polite, longCandidate), preExpansionImeHeightPx = 300)

        val height = composeRule.onNodeWithTag("personaspeak_candidate_body")
            .getUnclippedBoundsInRoot().height
        assertTrue(
            "review body $height exceeds the 120dp cap",
            height <= 120.dp,
        )
    }
}
