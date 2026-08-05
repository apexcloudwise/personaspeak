package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import org.junit.Assert.assertTrue
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSessionToken
import biz.pixelperfectstudios.personaspeak.ui.editor.EditorSnapshot
import biz.pixelperfectstudios.personaspeak.ui.editor.RequestGeneration
import biz.pixelperfectstudios.personaspeak.ui.editor.Utf16Selection
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The dedicated row is the only surface PersonaSpeak owns. These tests pin the
 * two geometry contracts that keep ASK's own rows usable: every interactive
 * control clears the 48dp touch minimum, and the Review body never grows past
 * the frozen min(320dp, 40% of the pre-expansion IME height) cap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RewritePanelTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val candidate = RewriteCandidate(
        snapshot = EditorSnapshot(
            session = EditorSessionToken(1L),
            generation = RequestGeneration(1L),
            draft = "synthetic draft",
            selection = Utf16Selection(0, 15),
        ),
        replacement = "synthetic replacement",
    )

    /**
     * A candidate long enough that an *uncapped* body would comfortably exceed
     * the cap. Without this the cap test passes whether or not the cap exists,
     * because short content never reaches the bound — a fixture that cannot
     * fail is not a test.
     */
    private val longCandidate = candidate.copy(
        // Hard line breaks, not a long paragraph: under Robolectric a
        // paragraph's wrap width is not reliable enough to guarantee height,
        // and a fixture whose height depends on text measurement can quietly
        // stop reaching the cap. 80 explicit lines always exceed it.
        replacement = (1..80).joinToString("\n") { "synthetic-line-$it" },
    )

    private fun setPanel(
        state: RewritePanelState,
        preExpansionImeHeightPx: Int = 1000,
    ) {
        composeRule.setContent {
            RewritePanel(
                state = state,
                onRewrite = {},
                onApply = {},
                onDismiss = {},
                onSettings = {},
                preExpansionImeHeightPx = { preExpansionImeHeightPx },
            )
        }
    }

    @Test
    fun `idle exposes a 48dp rewrite control and settings`() {
        setPanel(RewritePanelState.Idle)

        composeRule.onNodeWithTag("personaspeak_rewrite")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithTag("personaspeak_settings")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `loading exposes a loading indicator and keeps settings reachable`() {
        setPanel(RewritePanelState.Loading)

        composeRule.onNodeWithTag("personaspeak_loading").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_settings")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `review displays the candidate and 48dp apply and dismiss controls`() {
        setPanel(RewritePanelState.Review(candidate))

        composeRule.onNodeWithTag("personaspeak_candidate").assertIsDisplayed()
        composeRule.onNodeWithTag("personaspeak_apply")
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
    fun `message leaves settings reachable`() {
        setPanel(RewritePanelState.Message(RewriteMessage.ProviderFailure))

        composeRule.onNodeWithTag("personaspeak_settings")
            .assertIsDisplayed()
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun `review body honours the forty percent cap of the pre-expansion sample`() {
        // 300dp pre-expansion sample at Robolectric's default density of 1.0
        // gives 300px; forty percent is 120dp, below the 320dp hard cap.
        setPanel(RewritePanelState.Review(longCandidate), preExpansionImeHeightPx = 300)

        // Declared mechanism-only deviation from the corrective plan
        // (lines 595-598), authorised by the overseer: the plan names
        // `assertHeightIsAtMost`, which does not exist in
        // androidx.compose.ui:ui-test 1.8.2 — the artifact provides only
        // `assertHeightIsAtLeast`, equality assertions, and direct bounds
        // access. The invariant is unchanged: height <= 120.dp, measured
        // directly instead of through an unavailable convenience assertion.
        val height = composeRule.onNodeWithTag("personaspeak_candidate_body")
            .getUnclippedBoundsInRoot().height
        assertTrue(
            "review body $height exceeds the 120dp cap",
            height <= 120.dp,
        )
    }
}
