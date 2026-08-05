package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/** Android's touch-target floor. Every interactive control here clears it. */
private val MinInteractiveHeight = 48.dp

/**
 * PersonaSpeak's dedicated keyboard row.
 *
 * Two layouts, one Surface. Idle, Loading, and Message are a single compact
 * row. Review is a column: a scrollable candidate body bounded by
 * [resultBodyMaxHeightPx], then an action row. The bound is what keeps ASK's
 * suggestion strip and key rows visible — this row is allowed to grow, but
 * never far enough to cover the keyboard underneath it.
 *
 * [preExpansionImeHeightPx] reads container geometry only, and is sampled once
 * per candidate. It must not be sampled after Review lays out, because Review
 * is what changes the height being sampled.
 */
@Composable
fun RewritePanel(
    state: RewritePanelState,
    onRewrite: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    preExpansionImeHeightPx: () -> Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current

    // Frozen per candidate: a changed outcome on the same candidate keeps the
    // bound stable, and a new candidate samples the container again. `remember`
    // rather than `rememberSaveable` — content dimensions are not saved state.
    val reviewBodyMaxHeightPx =
        if (state is RewritePanelState.Review) {
            remember(state.candidate) {
                resultBodyMaxHeightPx(
                    preExpansionHeightPx = preExpansionImeHeightPx(),
                    density = density.density,
                )
            }
        } else {
            0
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        if (state is RewritePanelState.Review) {
            ReviewLayout(
                state = state,
                maxBodyHeightPx = reviewBodyMaxHeightPx,
                onApply = onApply,
                onDismiss = onDismiss,
                onSettings = onSettings,
            )
        } else {
            CompactLayout(
                state = state,
                onRewrite = onRewrite,
                onSettings = onSettings,
            )
        }
    }
}

/** Idle, Loading, and Message: one row, nothing to scroll. */
@Composable
private fun CompactLayout(
    state: RewritePanelState,
    onRewrite: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MinInteractiveHeight)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when (state) {
            is RewritePanelState.Idle -> {
                TextButton(
                    onClick = onRewrite,
                    modifier = Modifier
                        .heightIn(min = MinInteractiveHeight)
                        .testTag("personaspeak_rewrite"),
                ) {
                    Text("Rewrite")
                }
            }

            is RewritePanelState.Loading -> {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .testTag("personaspeak_loading"),
                    strokeWidth = 2.dp,
                )
            }

            is RewritePanelState.Message -> {
                Text(
                    text = state.kind.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("personaspeak_message"),
                )
            }

            // Review has its own layout; CompactLayout is never called with it.
            is RewritePanelState.Review -> Unit
        }

        SettingsButton(onSettings)
    }
}

/**
 * Review: candidate body over an action row.
 *
 * The body scrolls vertically inside the frozen bound. No horizontal scrolling
 * — a candidate that runs wide wraps and scrolls down, which is the only
 * direction that does not fight the keyboard's own gestures.
 */
@Composable
private fun ReviewLayout(
    state: RewritePanelState.Review,
    maxBodyHeightPx: Int,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
) {
    val density = LocalDensity.current
    // A zero bound means the container height was not sampleable. Leave the
    // body unbounded rather than collapsing it to nothing — an unreadable
    // candidate is a worse failure than a tall one, and the host's own
    // container still clips.
    val bodyModifier = if (maxBodyHeightPx > 0) {
        Modifier.heightIn(max = with(density) { maxBodyHeightPx.toDp() })
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = bodyModifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("personaspeak_candidate_body"),
        ) {
            Text(
                text = state.candidate.replacement,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("personaspeak_candidate"),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = MinInteractiveHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.outcome == null) {
                TextButton(
                    onClick = onApply,
                    modifier = Modifier
                        .heightIn(min = MinInteractiveHeight)
                        .testTag("personaspeak_apply"),
                ) {
                    Text("Use this")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .heightIn(min = MinInteractiveHeight)
                        .testTag("personaspeak_dismiss"),
                ) {
                    Text("Dismiss")
                }
            } else {
                val label = when (state.outcome) {
                    is RewriteOutcome.Applied -> "Applied"
                    is RewriteOutcome.Stale -> "Stale"
                    is RewriteOutcome.Rejected -> "Rejected"
                    is RewriteOutcome.Unconfirmed -> "Unconfirmed"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("personaspeak_outcome"),
                )
            }

            SettingsButton(onSettings)
        }
    }
}

@Composable
private fun SettingsButton(onSettings: () -> Unit) {
    IconButton(
        onClick = onSettings,
        modifier = Modifier
            .heightIn(min = MinInteractiveHeight)
            .testTag("personaspeak_settings"),
    ) {
        Icon(
            painter = painterResource(android.R.drawable.ic_menu_manage),
            contentDescription = "Settings",
            modifier = Modifier.size(20.dp),
        )
    }
}
