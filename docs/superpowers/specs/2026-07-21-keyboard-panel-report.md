# Keyboard panel report — the designed persona strip

**Branch:** `feat/keyboard-panel` (forked from `feat/graft-foundation`)
**Date:** 2026-07-21
**Scope:** replace the walking-skeleton `PersonaPanel.kt` with the real designed persona-strip UX from Stitch Set 2, inside the frozen `PersonaPanel(provider, onCommit, onSwitchBack)` signature. The `InputMethodService` call site and the panel's public contract are unchanged; everything else inside the panel was redesigned.

## What landed

All new code lives under `android/app/src/main/kotlin/biz/pixelperfectstudios/personaspeak/keyboard/`. The walking skeleton's one file is now eight, split by concern.

### `Mood.kt`
- `enum class Mood(val label: String)` with five values — `Polite`, `Witty`, `Blunt`, `Apologetic`, `Formal` — and a `val Moods: List<Mood>` catalog. UI-only; see *Mood modeling* below.

### `KeyboardPersonas.kt`
- `data class KeyboardPersona(id, emoji, displayName, subtitle, private persona: Persona)` exposing `fun systemPrompt(mood: Mood): String`.
- `val KeyboardPersonas: List<KeyboardPersona>` — the four launch personas (🎩 Jeeves default, 🏛️ Sir Humphrey, 🤠 Dr. Schultz, 🎬 Bachchan) with emoji and the mockup subtitles.
- `val DefaultKeyboardPersona = KeyboardPersonas.first()`.
- `systemPrompt(mood)` composes the real `PromptBuilder.build(persona)` system prompt and appends `"\n\nTone/mood for this rewrite: ${mood.label}."` — the mood wiring is honest, even though `FakeProvider` ignores the string.

### `TransformState.kt`
- `sealed interface TransformState { Idle; Loading; Success(text); Error(message) }` — the panel's state machine.

### `PersonaStrip.kt`
- `@Composable PersonaStrip(persona, mood, loading, onPickPersona, onPickMood, onTransform, modifier)` — the 40 dp resting row. Persona chip (pill, `surfaceContainerHighest` with a teal accent border), mood chip (pill, muted `surfaceContainerHigh`), and the circular transform FAB (40 dp, `Brush.linearGradient(Primary, Secondary)`, teal glow via `shadow`). The FAB shows `✦` at idle and a `CircularProgressIndicator` while loading.

### `PersonaPickerCard.kt`
- `@Composable PersonaPickerCard(personas, selectedId, onSelect, onDismiss, modifier)` — the 2×2 persona grid. Header "CHOOSE A CHARACTER" + close. Each tile carries emoji, name, and the mockup subtitle; the selected tile flips to `surfaceContainerHighest` with a teal border, primary-colored name, and a pulse dot.

### `MoodPickerCard.kt`
- `@Composable MoodPickerCard(moods, selected, onSelect, onDismiss, modifier)` — the narrow vertical popover (192 dp), five rows divided by `HorizontalDivider(outlineVariant)`. The selected row renders in `primary` with a `✓`; others in `onSurfaceVariant`.

### `ResultCard.kt`
- `@Composable ResultCard(state, persona, mood, onCommit, onAgain, onDismiss, onCancel, modifier)` — one parameterized card for every persona/mood combination (mockups 2.5 + 2.7/2.8/2.9). Branches on `TransformState`:
  - **Loading** — three `ShimmerBar` skeleton bars driven by a `rememberInfiniteTransition` (teal-tinted gradient sweep, 1100 ms), the caption *"Composing something regrettable…"*, and a "Cancel" text button.
  - **Success** — the rewritten text (italic, `bodyLarge`), header `${emoji} ${PERSONA} · ${MOOD}`, and an action row: "✓ Use this" (teal-gradient, `weight(1f)` → `onCommit(text)`), "↻ Again" (`surfaceContainerHighest` → `onAgain`), "✕" dismiss (→ `onDismiss`).
  - **Error** — the in-voice message + "Dismiss".
  - **Idle** — renders nothing.

### `PersonaPanel.kt` (rewritten, signature frozen)
- Holds the panel state: selected `persona`, `mood`, `draft`, `TransformState`, two picker-open flags, a coroutine `Job`. `runTransform()` validates the draft, builds the system prompt via `persona.systemPrompt(mood)`, and launches `provider.rewrite(...)` in a `rememberCoroutineScope`; selecting a new persona or mood resets state to `Idle` so no stale card lingers under a freshly-tapped chip.
- Wraps everything in `PersonaSpeakTheme` (the skeleton used a raw `MaterialTheme`).
- Layout: a `Surface(surfaceContainerLowest)` column — the floating zone (pickers or `ResultCard`) on top, the draft field, then `PersonaStrip`, then a right-aligned "⌨ Switch back to keyboard" affordance wired to `onSwitchBack`.

### `PersonaBoardService.kt` — untouched
The frozen call site still reads `PersonaPanel(provider, onCommit = { currentInputConnection?.commitText(reply, 1) }, onSwitchBack = { switchBackToPreviousKeyboard() })`.

## Mood modeling

Mood is new and UI-only. There is no `Mood` type in `core-personas` or `core-providers`, and none was added — AGENTS.md's module law keeps those two modules pure Kotlin with no Android imports, and a UI affordance doesn't earn a core-layer type. Mood is a five-value `enum` scoped to the keyboard package.

The mood reaches the provider as a tone suffix on the real system prompt: `PromptBuilder.build(persona) + "\n\nTone/mood for this rewrite: ${mood.label}."`. `FakeProvider` ignores the `system` argument entirely, so the wiring is latent today, but it means a real `CompletionProvider` implementation will receive persona context and the requested tone without the panel doing anything clever. No change to `PromptBuilder` (golden-pinned, desktop-shared) and no new core type.

## Draft capture — a known stub

The draft is still a local `TextField`, same as the walking skeleton, but styled to read as part of the resting strip rather than a bare `OutlinedTextField`. This is not the final design.

A real IME reads the host field through `currentInputConnection`. `PersonaPanel` is a pure `@Composable` with no `InputMethodService` access, and the task scoped this work to *not* restructure the service/panel boundary. The replacement is specified in [`docs/superpowers/specs/2026-07-21-stale-field-race-design.md`](2026-07-21-stale-field-race-design.md): an `EditorSnapshot` / `EditorAuthority` / `guardedRewrite` guard in pure Kotlin (core-providers) that captures `fieldId` + `contentHash` + `generation` + `selection` at request time and re-validates before commit, with the keyboard side implementing `EditorAuthority` from `InputConnection`.

`DraftField` carries a `TODO(host-text-capture)` KDoc block pointing at that spec, so the next worker to touch the boundary lands on the right doc.

## Blank-input handling

Tapping transform on an empty draft shows the house line verbatim, as an in-voice error card (not a modal):

> Type something first — even Jeeves needs material to work with.

The remaining failure states (no connection, invalid key, quota exhausted) are deliberately not built — `FakeProvider` never fails, so those branches aren't reachable yet. They wait for a real `CompletionProvider` implementation.

## Verification

`./gradlew :app:assembleDebug --no-daemon` is green from the `android/` worktree throughout. (Local JDK note only: the build needs a 17 toolchain and the Homebrew default on this machine is 26; that was satisfied via `~/.gradle/gradle.properties`, not a repo change.)

The APK was installed on emulator-5554 and the IME enabled and set as active. The host field was the Settings search box (`com.google.android.settings.intelligence`'s `open_search_view_edit_text`), focused by tapping "Search settings". Pixel sampling against the captured screenshots (the model has no image input, so a stdlib-only PNG decoder was used to confirm exact RGB values) confirmed every surface matched its design token — `#1C2026` SurfaceContainer for the cards, `#31353C` SurfaceContainerHighest for the selected persona tile, the `#4FDBC8` → `#4CD7F6` gradient on the FAB.

Five states were screenshotted:

1. **Resting strip** (`/tmp/kb_resting2.png`) — persona chip "🎩 Jeeves ▾" left, mood chip "polite ▾" beside it, transform FAB docked far-right with the teal-to-cyan gradient and the ✦ glyph.
2. **Persona picker** (`/tmp/kb_persona_picker.png`) — the `#1C2026` card with the 2×2 grid; Jeeves tile in `#31353C` (selected) with a teal border, the other three in `#262A31` (unselected); emoji and name glyphs present on every tile.
3. **Mood picker** (`/tmp/kb_mood_picker.png`) — the narrow 192 dp card right-aligned, five rows with dividers; "polite" row marked selected.
4. **Loading** (`/tmp/kb_loading2.png`) — caught mid-transform during `FakeProvider`'s 400 ms window (tap FAB; sleep 0.2; screencap). The card surface plus the teal-tinted shimmer sweep at the skeleton-bar row, the "Composing something regrettable…" caption, and the FAB in its spinner state.
5. **Result card** (`/tmp/kb_result.png`) — header + the FakeProvider's reply in italic body text + the "✓ Use this" teal-gradient button + "↻" and "✕".

End-to-end commit was confirmed by tapping "Use this" in the result card and dumping the UI hierarchy: the host Settings search field contained the FakeProvider's full reply string (`I have taken the liberty, sir, of rephrasing your words: "…" — though I must confess the genuine article is still en route.`), proving `onCommit` → `currentInputConnection.commitText(reply, 1)`.

After verification, Gboard (`com.google.android.inputmethod.latin/.LatinIME`) was restored as the active IME so the emulator can type normally.

### Caveat noted during verification

To film the loading and result states, the draft field had to be seeded with text from code rather than typed, because an `InputMethodService`'s own internal `BasicTextField` cannot receive `adb shell input text` injection — key events route to the host window, which holds input focus, not to the IME window. This is precisely the limitation the draft-stub TODO acknowledges. The seed was temporary and has been reverted to an empty draft in the committed code.

## Out of scope (by task contract)

- `PersonaBoardService.kt` structure and call signature — untouched.
- Real host-app text capture via `InputConnection` — the stale-field-race-design spec's job.
- Error states beyond blank-input — wait on a real provider.
- Drawing the result card over the host app window — IME windows can't; the gap analysis documents this and the card floats inside the IME's own window, same as the FlorisBoard spike did.
