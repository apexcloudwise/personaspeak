package biz.pixelperfectstudios.personaspeak.ui.rewrite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun RewritePanel(
    state: RewritePanelState,
    onRewrite: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        tonalElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state) {
                is RewritePanelState.Idle -> {
                    TextButton(
                        onClick = onRewrite,
                        modifier = Modifier.testTag("personaspeak_rewrite"),
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

                is RewritePanelState.Review -> {
                    Text(
                        text = state.candidate.replacement,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("personaspeak_candidate"),
                    )
                    if (state.outcome == null) {
                        TextButton(
                            onClick = onApply,
                            modifier = Modifier.testTag("personaspeak_apply"),
                        ) {
                            Text("Use this")
                        }
                        TextButton(onClick = onDismiss) {
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
                        )
                    }
                }

                is RewritePanelState.Message -> {
                    Text(
                        text = state.kind.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            IconButton(
                onClick = onSettings,
                modifier = Modifier.testTag("personaspeak_settings"),
            ) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_manage),
                    contentDescription = "Settings",
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
