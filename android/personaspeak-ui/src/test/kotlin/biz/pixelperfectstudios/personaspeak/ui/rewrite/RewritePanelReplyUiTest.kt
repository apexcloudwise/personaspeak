package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import biz.pixelperfectstudios.personaspeak.personas.IncomingMessageContext
import biz.pixelperfectstudios.personaspeak.personas.Mood
import biz.pixelperfectstudios.personaspeak.personas.Persona
import biz.pixelperfectstudios.personaspeak.personas.PersonaId
import biz.pixelperfectstudios.personaspeak.personas.PersonaProvenance
import biz.pixelperfectstudios.personaspeak.personas.ValidatedPersona
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose tests for the reply chip and suggestion cards (plan §4.3): the chip
 * appears only with a cached message, the three cards render with the reply
 * context, regenerate/dismiss/cancel are reachable, and content descriptions
 * exist for the a11y floor.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RewritePanelReplyUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val testPersona = ValidatedPersona(
        id = PersonaId.bundled("jeeves"),
        provenance = PersonaProvenance.bundled,
        content = Persona(
            name = "Jeeves",
            context = " (valet)",
            speechPatterns = listOf("Impeccably formal"),
        ),
    )

    private val incoming = IncomingMessageContext(
        sender = "Sam",
        appLabel = "Messages",
        text = "Running late, start the tea without me",
    )

    @Test
    fun `resting panel hides the chip when no message is cached`() {
        composeRule.setContent {
            RewritePanel(
                state = RewritePanelState.Resting(testPersona, Mood.DEFAULT),
                onRewrite = {},
                onApply = {},
                onDismiss = {},
                onSettings = {},
                preExpansionImeHeightPx = { 0 },
            )
        }

        composeRule.onNodeWithTag("personaspeak_reply_chip").assertDoesNotExist()
    }

    @Test
    fun `resting panel shows the replying-to chip with sender and app`() {
        composeRule.setContent {
            RewritePanel(
                state = RewritePanelState.Resting(testPersona, Mood.DEFAULT),
                onRewrite = {},
                onApply = {},
                onDismiss = {},
                onSettings = {},
                preExpansionImeHeightPx = { 0 },
                replyContext = incoming,
            )
        }

        composeRule.onNodeWithTag("personaspeak_reply_chip").assertIsDisplayed()
        composeRule.onNodeWithText("Sam · Messages").assertExists()
        composeRule.onNodeWithContentDescription("Replying to Sam · Messages. Tap to draft suggested replies.")
            .assertExists()
    }

    @Test
    fun `tapping the chip requests suggestions`() {
        var requested = 0
        composeRule.setContent {
            RewritePanel(
                state = RewritePanelState.Resting(testPersona, Mood.DEFAULT),
                onRewrite = {},
                onApply = {},
                onDismiss = {},
                onSettings = {},
                preExpansionImeHeightPx = { 0 },
                replyContext = incoming,
                onRequestSuggestions = { requested += 1 },
            )
        }

        composeRule.onNodeWithTag("personaspeak_reply_chip").performClick()

        assertEquals(1, requested)
    }

    @Test
    fun `suggesting state shows the drafting indicator and cancel`() {
        composeRule.setContent {
            RewritePanel(
                state = RewritePanelState.Suggesting(testPersona, Mood.DEFAULT, incoming, "key-1"),
                onRewrite = {},
                onApply = {},
                onDismiss = {},
                onSettings = {},
                preExpansionImeHeightPx = { 0 },
            )
        }

        composeRule.onNodeWithTag("personaspeak_suggesting").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_suggest_cancel").assertIsDisplayed()
    }

    @Test
    fun `suggestions state renders the context header and three cards`() {
        composeRule.setContent {
            RewritePanel(
                state = RewritePanelState.Suggestions(
                    persona = testPersona,
                    mood = Mood.DEFAULT,
                    context = incoming,
                    conversationKey = "key-1",
                    replies = listOf("Right away", "Shall I bring dessert", "See you at six"),
                ),
                onRewrite = {},
                onApply = {},
                onDismiss = {},
                onSettings = {},
                preExpansionImeHeightPx = { 0 },
            )
        }

        composeRule.onNodeWithTag("personaspeak_suggestions_context").assertIsDisplayed()
        for (index in 0..2) {
            composeRule.onNodeWithTag("personaspeak_suggestion_$index").assertIsDisplayed()
        }
        composeRule.onNodeWithText("Right away").assertExists()
        composeRule.onNodeWithContentDescription("Suggested reply 2: Shall I bring dessert. Tap to insert as draft.")
            .assertExists()
        composeRule.onNodeWithTag("personaspeak_suggestion_regenerate").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_suggestion_dismiss").assertIsDisplayed()
    }

    @Test
    fun `tapping a suggestion card applies that index`() {
        var appliedIndex = -1
        composeRule.setContent {
            RewritePanel(
                state = RewritePanelState.Suggestions(
                    persona = testPersona,
                    mood = Mood.DEFAULT,
                    context = incoming,
                    conversationKey = "key-1",
                    replies = listOf("one", "two", "three"),
                ),
                onRewrite = {},
                onApply = {},
                onDismiss = {},
                onSettings = {},
                preExpansionImeHeightPx = { 0 },
                onApplySuggestion = { appliedIndex = it },
            )
        }

        composeRule.onNodeWithTag("personaspeak_suggestion_1").performClick()

        assertEquals(1, appliedIndex)
    }
}
