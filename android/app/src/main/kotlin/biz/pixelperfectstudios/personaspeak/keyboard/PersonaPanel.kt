package biz.pixelperfectstudios.personaspeak.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import biz.pixelperfectstudios.personaspeak.app.ui.theme.PersonaSpeakTheme
import biz.pixelperfectstudios.personaspeak.providers.CompletionProvider
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// The blank-input guard line, lifted verbatim from
// docs/superpowers/specs/2026-07-21-prototype-gap-analysis.md §6.1. Keep the
// wording identical so the gap analysis and the product agree on the voice.
private const val BLANK_DRAFT_MESSAGE =
    "Type something first — even Jeeves needs material to work with."

/**
 * The PersonaSpeak keyboard panel — the real designed persona strip (mockup
 * set 2.1–2.9), replacing the walking skeleton that used to live here.
 *
 * Public signature is frozen: [PersonaBoardService] calls this from
 * `onCreateInputView()` and wires [onCommit] to `commitText`. Redesigns stay
 * inside this file.
 *
 * Pickers and the result card are inline overlays, not `Popup`s — the IME
 * window token does not reliably host child windows, so everything renders in
 * the panel's own composition (see 2026-07-21-prototype-gap-analysis.md).
 */
@Composable
fun PersonaPanel(
    provider: CompletionProvider,
    onCommit: (String) -> Unit,
    onSwitchBack: () -> Unit,
) {
    var persona by remember { mutableStateOf(DefaultKeyboardPersona) }
    var mood by remember { mutableStateOf(Mood.Polite) }
    var draft by remember { mutableStateOf("") }
    var state by remember { mutableStateOf<TransformState>(TransformState.Idle) }
    var personaPickerOpen by remember { mutableStateOf(false) }
    var moodPickerOpen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }

    fun runTransform() {
        if (draft.isBlank()) {
            state = TransformState.Error(BLANK_DRAFT_MESSAGE)
            return
        }
        job?.cancel()
        state = TransformState.Loading
        val system = persona.systemPrompt(mood)
        job = scope.launch {
            provider.rewrite(system = system, text = draft)
                .onSuccess { state = TransformState.Success(it) }
                .onFailure { state = TransformState.Error(it.message ?: "The butler balked.") }
        }
    }

    fun cancelTransform() {
        job?.cancel()
        job = null
        state = TransformState.Idle
    }

    fun selectPersona(p: KeyboardPersona) {
        persona = p
        personaPickerOpen = false
        // A stale result under a new chip would mislead; clear it.
        state = TransformState.Idle
    }

    fun selectMood(m: Mood) {
        mood = m
        moodPickerOpen = false
        state = TransformState.Idle
    }

    PersonaSpeakTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    when {
                        personaPickerOpen -> PersonaPickerCard(
                            personas = KeyboardPersonas,
                            selectedId = persona.id,
                            onSelect = ::selectPersona,
                            onDismiss = { personaPickerOpen = false },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )

                        moodPickerOpen -> MoodPickerCard(
                            moods = Moods,
                            selected = mood,
                            onSelect = ::selectMood,
                            onDismiss = { moodPickerOpen = false },
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )

                        else -> ResultCard(
                            state = state,
                            persona = persona,
                            mood = mood,
                            onCommit = { text ->
                                onCommit(text)
                                state = TransformState.Idle
                            },
                            onAgain = { runTransform() },
                            onDismiss = { state = TransformState.Idle },
                            onCancel = { cancelTransform() },
                            modifier = Modifier.align(Alignment.BottomCenter),
                        )
                    }
                }

                DraftField(draft = draft, onDraftChange = { draft = it })

                PersonaStrip(
                    persona = persona,
                    mood = mood,
                    loading = state is TransformState.Loading,
                    onPickPersona = {
                        personaPickerOpen = !personaPickerOpen
                        moodPickerOpen = false
                    },
                    onPickMood = {
                        moodPickerOpen = !moodPickerOpen
                        personaPickerOpen = false
                    },
                    onTransform = { runTransform() },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "⌨ Switch back to keyboard",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable(onClick = onSwitchBack),
                    )
                }
            }
        }
    }
}

/**
 * Draft capture — STUB. A real IME reads the host field via `InputConnection`,
 * but [PersonaPanel] is a pure @Composable with no `InputMethodService` access,
 * so we keep a local text field to drive the demo. Styled to read as part of
 * the resting strip, not a bare outlined field.
 *
 * TODO(host-text-capture): replace this local TextField with real
 * `currentInputConnection` text capture behind the stale-field guard spec'd in
 * docs/superpowers/specs/2026-07-21-stale-field-race-design.md
 * (EditorSnapshot / EditorAuthority / guardedRewrite). That work restructures
 * the service↔panel boundary on purpose and is out of scope for the panel
 * redesign — do NOT wire InputConnection in from here.
 */
@Composable
private fun DraftField(draft: String, onDraftChange: (String) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (draft.isEmpty()) {
                Text(
                    text = "Type your draft — the butler is listening.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BasicTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = false,
                maxLines = 3,
            )
        }
    }
}
