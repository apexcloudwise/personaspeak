package biz.pixelperfectstudios.personaspeak.keyboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import kotlinx.coroutines.launch

/**
 * Walking-skeleton panel: one hardcoded persona chip, a text field, a
 * transform button, one result card. The real chips/tones/suggestions UI
 * lands in GTM Day 5 — this exists to prove the IME wiring end to end.
 */
@Composable
fun PersonaPanel(
    provider: CompletionProvider,
    onCommit: (String) -> Unit,
    onSwitchBack: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    MaterialTheme {
        Surface {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("🎩 Jeeves", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onSwitchBack) { Text("⌨ back") }
                }

                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Your words, before the butler gets them") },
                )

                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            result = provider.rewrite(system = "", text = draft).getOrElse { it.message }
                            busy = false
                        }
                    },
                    enabled = draft.isNotBlank() && !busy,
                ) {
                    if (busy) CircularProgressIndicator(modifier = Modifier.padding(2.dp))
                    else Text("Transform")
                }

                result?.let { reply ->
                    Card(onClick = { onCommit(reply) }, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            reply,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}
