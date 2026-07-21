# PersonaSpeak — Stitch Mockups Implementation Spec

**Date:** 2026-07-21  
**Source Material:** `stitch_personaspeak_ui_mockups/` (24 exported screen folders), `docs/design/stitch-prompt.md`, `stitch_personaspeak_ui_mockups/personaspeak_design_system/DESIGN.md`  
**Cross-Reference:** `docs/superpowers/specs/2026-07-21-prototype-gap-analysis.md`  
**Purpose:** Technical implementation specification for an engineer building the Android Compose UI (Settings App, Onboarding Flow, IME overlays, and Error States) for ADR-0004 Slices 4 and 5.

---

## 1. Executive Summary & Inventory Coverage

### Coverage Statistics
- **Total Spec Screens:** 36 numbered screens across 6 sets (`docs/design/stitch-prompt.md`).
- **Exported Mockup Folders:** 24 screen folders in `stitch_personaspeak_ui_mockups/` (excluding `shader/` asset folder and `personaspeak_design_system/` token file).
- **Direct Mappings:** 18 exported folders map 1-to-1 to primary screen numbers.
- **Design Variants:** 6 exported folders represent alternative design explorations for 2 specific screen numbers (4 variants for Screen 1.3, 2 variants for Screen 6.4).
- **Unmocked Spec Screens:** 12 screens from the 36-screen spec have no dedicated exported mockup folder.

### Unmocked Screens Matrix

| Screen | Name | Reason / Implementation Guidance |
|---|---|---|
| **2.4** | Loading / Thinking state | Mockup omitted. Implemented in prototype Compose (`ResultCard.kt`) using shimmer bars (`#14B8A6` to transparent) and "Composing something regrettable…" caption. |
| **2.6** | After 'Use this' — text replaced | State transition only. IME input connection commits text to host field; UI returns to Screen 2.1 resting strip state. |
| **2.7** | Dr. Schultz result | Variant of Screen 2.5. Uses header `DR. SCHULTZ · witty` and Dr. Schultz cannon prompt output. |
| **2.8** | Sir Humphrey result | Variant of Screen 2.5. Uses header `SIR HUMPHREY · formal` and Sir Humphrey cannon prompt output. |
| **2.9** | Bachchan result | Variant of Screen 2.5. Uses header `BACHCHAN · blunt` and Bachchan cannon prompt output. |
| **3.4** | Error: Empty/malformed response | Variant of Screen 3.1. Uses headline "Something went sideways" and body copy "The response arrived in a state that, I regret to say, defied interpretation." |
| **4.5** | Rewrite behaviour settings | Sub-screen of Settings. Radio group ("Ask before replacing" vs "Replace immediately") with muted recovery note below. |
| **5.1–5.5** | Dark Mode standalone screens | Sets 2, 3, 4, and 6 in Stitch are designed dark-first (`#10141a` / `#0D1117`). Explicit separate light/dark exports were not generated. |
| **6.1** | Empty input | Triggered when transform is tapped with empty draft. Display a floating Compose `Snackbar` near strip: "Type something first — even Jeeves needs material to work with." |
| **6.3** | Confirmation dialog — Replace text | Confirmation popup over result card when "Ask before replacing" is enabled. Shows original text (strikethrough) and new text with "Replace" / "Keep mine" actions. |
| **6.5** | Keyboard with no AI configured | Stripped IME state. Transform FAB is replaced by a muted "Set up ↗" text link opening Onboarding/Settings flow. |
| **6.6** | Landscape mode | IME layout adaptation. Strip collapses to single compact line; result card height is capped at 30% viewport height with horizontal action row. |

---

## 2. Design Variants & Build Recommendations

### Screen 1.3 — Pick a Brain (4 Variants)
1. `onboarding_ai_selection` (Standard Stack): Vertical list of 5 provider cards with radio buttons, "Recommended to start" badge on Gemini, and "SOON" greyed badge on On-Device.
2. `onboarding_ai_selection_high_density`: Compact technical metrics list featuring context size, latency badges, and fine-grained specs.
3. `onboarding_ai_selection_minimalist`: Single-column list with hairline dividers, simple radio toggles, and minimal text.
4. `onboarding_ai_selection_visual_cards`: 2-column grid featuring glowing background gradient blobs behind provider icons.

> [!TIP]
> **Build Recommendation:** Implement **`onboarding_ai_selection` (Standard Stack)**.  
> *Rationale:* It complies strictly with Material3 touch target guidelines (minimum 48dp per row), provides clear visual hierarchy on standard 6.1" portrait displays without cluttering, and includes explicit badges for Gemini ("FREE", "Recommended to start") and On-Device ("SOON").

### Screen 6.4 — First Transformation Celebration (2 Variants)
1. `edge_case_first_transformation_celebration`: Static celebration card with top banner (`Your first transformation! 🎉`) and a lightweight CSS/HTML particle scatter.
2. `edge_case_animated_celebration`: Floating result card over an active WebGL shader background (`STITCH_SHADER_START:ANIMATION_25`) rendering animated teal/cyan ambient light particles.

> [!TIP]
> **Build Recommendation:** Implement **`edge_case_first_transformation_celebration` (Static Banner + Compose Canvas Particles)** for Slice 5.  
> *Rationale:* AGSL `RuntimeShader` (`edge_case_animated_celebration`) requires Android 13+ (API 33) and adds rendering overhead inside the IME input window. A lightweight Compose `Canvas` particle burst maintains compatibility down to API 26.

---

## 3. Detailed Screen Specifications

### Set 1: Onboarding Flow

#### 1.1 Welcome
* **Spec Mapping:** Screen 1.1 — "Welcome"
* **Mockup Folder:** `onboarding_welcome` (Primary)
* **Purpose:** Introduces PersonaSpeak's core value proposition as an Android keyboard with manners.
* **Key Components:**
  * *Top Hero Illustration:* Floating illustration container (`#181c22` surface) with top hat emoji and keyboard grid motif.
  * *Headlines & Body:* Outfit 28sp semibold headline `"A keyboard with better manners"`; Inter 15sp body `"PersonaSpeak is a full keyboard — everything you expect, plus a row of characters who rewrite your words. Type 'cant make it sorry', tap the top hat, and Jeeves declines on your behalf with honour."`
  * *Avatars Row:* 4 circular avatar chips: `🎩 Jeeves`, `🏛️ Humphrey`, `🤠 Schultz`, `🎬 Bachchan`.
  * *Reassurance Badges:* Horizontal row of pill chips (`#1c2026` surface, 1px border `#3c4947`): `✓ Autocorrect`, `✓ Glide typing`, `✓ Works offline`.
  * *Primary CTA:* Teal gradient button (`linear-gradient(135deg, #14b8a6, #06b6d4)`), 12dp rounded: `"Get started"`.
  * *Secondary CTA:* Text link: `"I've done this before — skip setup"`.
* **Navigation:** `"Get started"` → Screen 1.2 (`onboarding_setup`). `"Skip setup"` → Settings Home (`settings_home`).
* **State / Data Implied:** Static onboarding state (`OnboardingStep.WELCOME`).
* **Flags for Implementation:** Light background variant in spec, but mockup uses dark theme `#10141a`. Use Material3 `Scaffold` with `WindowInsets` edge-to-edge handling.

#### 1.2 Make It Your Keyboard
* **Spec Mapping:** Screen 1.2 — "Make it your keyboard"
* **Mockup Folder:** `onboarding_setup` (Primary)
* **Purpose:** Guides the user through enabling PersonaSpeak in Android IME settings and selecting it as default.
* **Key Components:**
  * *App Bar:* Top header with back arrow and `"PersonaSpeak"` title.
  * *Headlines:* Outfit 22sp `"Make it your keyboard"`, Inter 14sp `"Two quick steps. You can always switch back."`
  * *Step 1 Card:* Surface `#1c2026`, 16dp rounded. Header `"Enable PersonaSpeak"`, subtitle `"Open keyboard settings and turn on PersonaSpeak"`, status indicator circle, primary button `"Open settings"`.
  * *Step 2 Card:* Surface `#1c2026` (opacity 0.6 when incomplete). Header `"Set as default"`, subtitle `"Switch your active keyboard to PersonaSpeak"`, button `"Switch keyboard"`.
  * *Privacy Callout:* Amber container `#e49200`/10 with warning icon ⚠️: `"Android will warn that this keyboard can see what you type. We explain what we do (and don't) with that access on the privacy page."`
  * *System Dialog Overlay:* Semi-transparent modal overlay depicting Android system IME warning dialog with `"Cancel"` / `"OK"`.
* **Navigation:** `"Open settings"` → `ACTION_INPUT_METHOD_SETTINGS`. `"Switch keyboard"` → `InputMethodManager.showInputMethodPicker()`. On both complete → Screen 1.3 (`onboarding_ai_selection`).
* **State / Data Implied:** `isImeEnabled: Boolean`, `isImeDefault: Boolean`. Reactive polling via `LifecycleEventObserver`.
* **Flags for Implementation:** Requires real-time IME state detection via `InputMethodManager`.

#### 1.3 Pick a Brain (Standard Stack)
* **Spec Mapping:** Screen 1.3 — "Pick a brain"
* **Mockup Folder:** `onboarding_ai_selection` (Primary)
* **Purpose:** Allows the user to choose their preferred LLM backend provider.
* **Key Components:**
  * *Header & Top Action:* Headline `"Pick a brain"`, subtext `"PersonaSpeak needs an AI to do the rewriting. Pick one now — you can change it later."` Top-right text link `"Skip"`.
  * *Provider Radio Group (5 Stacked Cards):*
    1. *Gemini:* Teal border `#14b8a6`, checked radio. Header `"Gemini"`, subtext `"Google's free tier. Works straight away — you just need a free key."`, badges: `"FREE"` (teal background), `"ⓘ Recommended to start"`.
    2. *Claude:* Header `"Claude"`, subtext `"Anthropic. Best persona fidelity. Your own key, billed to you."`
    3. *OpenAI:* Header `"OpenAI"`, subtext `"GPT models. Your own key, billed to you."`
    4. *OpenRouter:* Header `"OpenRouter"`, subtext `"One key, many models. Your own key."`
    5. *On this phone:* Muted surface, greyed text, disabled state. Header `"On this phone"`, subtext `"Runs locally. Nothing leaves the device. Coming in a later release."`, badge `"SOON"`.
  * *Primary CTA:* Bottom fixed bar with teal gradient button `"Continue →"`.
* **Navigation:** `"Continue →"` → Screen 1.4 (`onboarding_api_key`). `"Skip"` → Screen 1.5 (`onboarding_demo`).
* **State / Data Implied:** `selectedProvider: ProviderType` (`GEMINI`, `CLAUDE`, `OPENAI`, `OPENROUTER`, `LOCAL`).
* **Flags for Implementation:** Use Compose `RadioButton` combined with selectable `Card` containers.

#### 1.3 Pick a Brain — High-Density Variant
* **Spec Mapping:** Screen 1.3 (Variant)
* **Mockup Folder:** `onboarding_ai_selection_high_density`
* **Purpose:** Alternative provider selection showing latency, context window, and technical metrics.
* **Key Components:** Compact rows with metrics tags (`"200k Context"`, `"Ultra-Fast"`, `"Free Tier"`). Secure SSL footer badge.

#### 1.3 Pick a Brain — Minimalist Variant
* **Spec Mapping:** Screen 1.3 (Variant)
* **Mockup Folder:** `onboarding_ai_selection_minimalist`
* **Purpose:** Clean, low-density layout using thin dividers and minimal text.
* **Key Components:** Single-column list with hairline borders (`#3c4947`), plain radio toggles, pill CTA.

#### 1.3 Pick a Brain — Visual Cards Variant
* **Spec Mapping:** Screen 1.3 (Variant)
* **Mockup Folder:** `onboarding_ai_selection_visual_cards`
* **Purpose:** Highly visual 2-column grid with provider-branded gradient blobs.
* **Key Components:** `LazyVerticalGrid` of cards featuring blurred background color blobs (Gemini blue/teal, Claude orange, OpenAI emerald).

#### 1.4 Your Key, Your Business
* **Spec Mapping:** Screen 1.4 — "Your key, your business"
* **Mockup Folder:** `onboarding_api_key` (Primary)
* **Purpose:** Collects and validates the user's API key for secure Android Keystore storage.
* **Key Components:**
  * *Headlines:* Outfit 28sp `"Your key, your business"`, body `"Paste your Gemini API key. It goes straight into Android's encrypted Keystore — we never see it, and it never leaves your phone except to talk to Google."`
  * *Instruction Steps Card:* Surface `#1c2026`, containing:
    - Step ① `"Open Google AI Studio"` with outlined button `"Open in browser ↗"`.
    - Step ② `"Create a key and copy it"`.
  * *API Key Field:* Label `"API key"`. Input field using `System Monospace` font, masked dots `••••••••••••••••`, trailing eye toggle icon (`visibility` / `visibility_off`). Validation indicator below: teal text `✓ "Key looks valid"`.
  * *Security Callout:* Surface `#181c22` with lock icon 🔒: `"Stored in Android Keystore. Not synced, not backed up, not sent to us. Delete it any time in Settings."`
  * *Action CTAs:* Primary teal button `"Test and continue"`, text link `"Skip — I'll add it later"`.
* **Navigation:** `"Open in browser ↗"` → External intent `https://aistudio.google.com/app/apikey`. `"Test and continue"` → Validates key via provider test ping → Screen 1.5 (`onboarding_demo`).
* **State / Data Implied:** `apiKeyInput: String`, `isKeyValid: Boolean`, `isTestingKey: Boolean`, `isPasswordVisible: Boolean`.
* **Flags for Implementation:** Must write key via `EncryptedSharedPreferences` / `MasterKeys` (ADR-0005). Use Compose `PasswordVisualTransformation`.

#### 1.5 Try It
* **Spec Mapping:** Screen 1.5 — "Try it"
* **Mockup Folder:** `onboarding_demo` (Primary)
* **Purpose:** Interactive tutorial sandbox demonstrating text rewriting in a simulated chat.
* **Key Components:**
  * *Fake Chat Header & Messages:* Contact `"Alex"`. Received bubble `"Are you coming to dinner tonight?"`, draft text in input field `"cant make it sorry"`.
  * *Persona Strip:* Docked above keys: `[ 🎩 Jeeves ⌄ ]` `[ polite ⌄ ]` `[ → ]`.
  * *Interactive Tooltip Arrow:* Animated floating callout pointing at transform FAB: `"Tap here to transform"`.
  * *Floating Result Card:* Surface `#1c2026`, glass backdrop (`rgba(22, 27, 34, 0.85)`). Header `"JEEVES · polite"`. Body `"I regret to report, sir, that circumstances have conspired against my attendance this evening."` Buttons: `"Use this"` (teal gradient) and `"↻ Again"`.
  * *Keyboard:* Full dark charcoal QWERTY keyboard `#2d333b`.
* **Navigation:** Tapping `"Use this"` → Replaces input text → Completes onboarding → Navigates to Settings or exits to system.
* **State / Data Implied:** Simulated IME state machine (`draftText`, `transformedText`, `isOverlayVisible`).
* **Flags for Implementation:** Can be built as a standalone Compose screen in `app/` without attaching to real system `InputConnection`.

---

### Set 2: Keyboard in Action

#### 2.1 Resting State / Strip
* **Spec Mapping:** Screen 2.1 — "Resting state / Strip"
* **Mockup Folder:** `keyboard_resting_state` (Primary)
* **Purpose:** Default resting state of the IME showing active persona controls above keycaps.
* **Key Components:**
  * *Persona Strip (40dp Height):* Single horizontal row docked above keyboard surface `#1c2026`:
    - Left: Pill chip `#1c2128` with 2px teal left border: `🎩 Jeeves ⌄`.
    - Center: Muted pill chip: `polite ⌄`.
    - Right: Circular 40dp FAB with teal-cyan gradient fill and white right-arrow icon (`→`).
  * *Suggestion Row:* 3 word suggestions (`"I'm"`, `"I"`, `"I'll"`).
  * *Keycaps:* Dark charcoal `#2d333b` keys on `#1c2128` background, 4px corner radius (`rounded-sm`).
* **Navigation:** Tap Persona Chip → Screen 2.2 (`keyboard_persona_picker`). Tap Mood Chip → Screen 2.3 (`keyboard_mood_picker`). Tap Transform FAB → Triggers async API call → Screen 2.4 (Loading) → Screen 2.5 (Result Card).
* **State / Data Implied:** `activePersona: Persona`, `activeMood: Mood`, `editorText: String`, `canTransform: Boolean` (false if editor text blank).
* **Flags for Implementation:** Built inside Android `InputMethodService` view tree (`ComposeView`). Must enforce 48dp minimum touch targets for strip chips.

#### 2.2 Persona Picker Open
* **Spec Mapping:** Screen 2.2 — "Persona picker open"
* **Mockup Folder:** `keyboard_persona_picker` (Primary)
* **Purpose:** Expanded popup sheet allowing the user to switch active characters.
* **Key Components:**
  * *Glass Backdrop:* `rgba(22, 27, 34, 0.85)` overlay with `16px` backdrop blur (`RenderEffect` on Android 12+).
  * *Header:* Outfit 14sp uppercase `"CHOOSE A CHARACTER"`, trailing `✕` close icon button.
  * *2×2 Character Grid:*
    1. `🎩 Jeeves` — `"the impeccable valet"` (Active state: teal border `#14b8a6`, subtle teal glow).
    2. `🏛️ Sir Humphrey` — `"will neither confirm nor deny"`.
    3. `🤠 Dr. Schultz` — `"alarmingly courteous"`.
    4. `🎬 Bachchan` — `"delivered to a full house"`.
  * *Footer Link:* `"+ Browse all characters"` (deeplinks to Settings Persona Browser).
* **Navigation:** Tap character tile → Selects persona, dismisses sheet, updates strip. Tap `✕` → Dismisses sheet. Tap footer link → Opens Settings app (`settings_persona_browser`).
* **State / Data Implied:** `availablePersonas: List<Persona>`, `selectedPersonaId: String`.
* **Flags for Implementation:** Must render inside the IME window without obscuring the physical QWERTY keycaps below.

#### 2.3 Mood Picker Open
* **Spec Mapping:** Screen 2.3 — "Mood picker open"
* **Mockup Folder:** `keyboard_mood_picker` (Primary)
* **Purpose:** Popover list for adjusting the active transformation tone.
* **Key Components:**
  * *Popover Card:* Anchored directly above the Mood Chip. Surface `#161B22`, 12dp rounded, 1px border `rgba(255,255,255,0.08)`.
  * *Vertical List (5 Rows):*
    1. `polite` (Selected: teal checkmark `✓`, text `#4fdbc8`).
    2. `witty`.
    3. `blunt`.
    4. `apologetic`.
    5. `formal`.
* **Navigation:** Tap mood row → Updates active mood, dismisses popover.
* **State / Data Implied:** `availableMoods: List<Mood>`, `activeMood: Mood`.
* **Flags for Implementation:** Use Compose `Popup` or custom anchored `Column` inside the IME window layout.

#### 2.5 Result Card — Success
* **Spec Mapping:** Screen 2.5 — "Result card — success"
* **Mockup Folder:** `keyboard_result_jeeves` (Primary)
* **Purpose:** Floating card presenting the rewritten text for user review and action.
* **Key Components:**
  * *Floating Card Container:* Surface `#161B22` (85% opacity, 16px blur), 16dp rounded (`rounded-lg`), 1px border `rgba(255,255,255,0.06)`.
  * *Card Header:* Persona emoji + Outfit semibold title `"JEEVES · polite"`, trailing `✕` dismiss button.
  * *Result Body:* Inter 15sp `#E6EDF3` text: `"I regret to report, sir, that circumstances have conspired against my attendance this evening."`
  * *Action Row:*
    - Primary CTA: Teal gradient fill, 12dp rounded: `"Use this"`.
    - Secondary CTA: Text button: `"↻ Again"`.
* **Navigation:** `"Use this"` → Executes `InputConnection.commitText()`, replaces active field text, dismisses card. `"↻ Again"` → Re-triggers API transform call. `✕` → Dismisses card, retains draft text in field.
* **State / Data Implied:** `transformationResult: String`, `generationToken: String` (stale-field race guard per ADR-0003).
* **Flags for Implementation:** Must capture `EditorInfo` and selection offsets before invoking async transform to prevent text injection races.

---

### Set 3: Error States

#### 3.1 Error: No Connection
* **Spec Mapping:** Screen 3.1 — "Error: No connection"
* **Mockup Folder:** `error_no_connection` (Primary)
* **Purpose:** Notifies the user of network loss during a transformation attempt.
* **Key Components:**
  * *Error Card Container:* Surface `rgba(22, 27, 34, 0.85)`, 16px blur. Top border accent: 2px solid amber `#F59E0B`.
  * *Icon & Title:* Amber warning icon ⚠️ inside `rgba(245, 158, 11, 0.2)` circle. Outfit semibold `"No connection"`.
  * *Body Copy:* Inter 13sp `"I am afraid the internet has deserted us, sir. Your words are untouched."`
  * *Action Row:* Left text button `"Dismiss"`, right teal gradient button `"Try again"`.
* **Navigation:** `"Dismiss"` → Clears error card. `"Try again"` → Retries network request.
* **State / Data Implied:** `ErrorState.NoConnection`, preserving `draftText`.
* **Flags for Implementation:** Warm amber styling (`#F59E0B`), not harsh red, per VOICE.md design guidelines.

#### 3.2 Error: Invalid API Key
* **Spec Mapping:** Screen 3.2 — "Error: Invalid API key"
* **Mockup Folder:** `error_invalid_api_key` (Primary)
* **Purpose:** Informs the user that their stored API key was rejected by the provider HTTP API.
* **Key Components:**
  * *Card Container:* Surface `rgba(26, 20, 10, 0.9)`, 20px blur, border `rgba(255, 185, 95, 0.2)`.
  * *Title & Copy:* Outfit semibold `"Key not accepted"`, body `"The provider has declined your API key, sir. One suspects a typo, or perhaps an expired credential. The key can be updated in Settings."`
  * *Actions:* Left text link `"Dismiss"`, right bold teal link `"Open Settings"`.
* **Navigation:** `"Open Settings"` → Launches Settings App (`settings_ai_providers`).
* **State / Data Implied:** `ErrorState.InvalidKey`.
* **Flags for Implementation:** Uses Android deep link Intent to launch settings activity directly to the provider key section.

#### 3.3 Error: Quota Exhausted
* **Spec Mapping:** Screen 3.3 — "Error: Quota exhausted"
* **Mockup Folder:** `error_quota_exhausted` (Primary)
* **Purpose:** Informs the user that their monthly API token quota has been depleted.
* **Key Components:**
  * *Card Container:* Frosted glass card with amber top border accent.
  * *Title & Copy:* Outfit semibold `"Quota reached"`, body `"The provider reports your allocation for the period has been exhausted, sir. Your text remains as you wrote it."`
  * *Actions:* Left text button `"Try again tomorrow"`, right text button `"Dismiss"`.
* **Navigation:** Both actions dismiss the error card.
* **State / Data Implied:** `ErrorState.QuotaExhausted`.
* **Flags for Implementation:** Non-blocking advisory overlay.

#### 3.5 No Provider Configured
* **Spec Mapping:** Screen 3.5 — "No provider configured"
* **Mockup Folder:** `error_no_provider_configured` (Primary)
* **Purpose:** Prompts the user to configure an AI backend when attempting to transform without one.
* **Key Components:**
  * *Card Container:* Frosted glass surface.
  * *Header Icon:* Teal brain/lightbulb icon (`psychology`) inside circular `#14B8A6`/10 background.
  * *Title & Copy:* Outfit 22sp `"No brain connected"`, body `"PersonaSpeak needs an AI provider to rewrite your text. Set one up in Settings — it takes 30 seconds."`
  * *Actions:* Full-width teal gradient button `"Set up now"`, text link `"Not now"`.
* **Navigation:** `"Set up now"` → Launches Settings App (`settings_ai_providers`). `"Not now"` → Dismisses card.
* **State / Data Implied:** `ErrorState.NoProvider`.
* **Flags for Implementation:** Displayed whenever `EncryptedSharedPreferences` contains no valid key on transform press.

---

### Set 4: Settings App

#### 4.1 Settings Home
* **Spec Mapping:** Screen 4.1 — "Settings home"
* **Mockup Folder:** `settings_home` (Primary)
* **Purpose:** Main settings navigation dashboard for app configuration.
* **Key Components:**
  * *App Bar:* Top Hat icon in teal + Outfit headline `"PersonaSpeak"`, trailing search icon.
  * *Status Banner:* Glass card with green check icon: `"✓ PersonaSpeak is your default keyboard"`.
  * *Grouped Settings Lists (5 Muted Uppercase Headers):*
    1. *CHARACTERS:* `Personas` → "4 installed · Jeeves is default", `Default mood` → "Polite", `Rewrite behaviour` → "Ask before replacing text".
    2. *THE BRAIN:* `AI provider` → "Gemini · Free tier", `API key` → "1 key stored in Keystore", `Usage this month` → "312 rewrites".
    3. *TYPING:* `Languages and layouts` → "English (US) · QWERTY", `Glide typing` → Toggle ON (teal track), `Autocorrect` → Toggle ON (teal track), `Personal dictionary` → "18 words".
    4. *APPEARANCE:* `Theme` → "Follow system", `Keyboard height` → "Medium".
    5. *PRIVACY:* `What we store` → "Nothing. Here's the proof.", `Read the source` → "github.com/apexcloudwise/personaspeak" (external icon).
  * *Bottom Navigation Bar:* 2 tabs: `⌨ Personas` and `⚙ Settings` (Settings active with teal indicator).
* **Navigation:** Row taps navigate to detailed sub-screens. `Personas` tab switches to Screen 4.2 (`settings_persona_browser`).
* **State / Data Implied:** Full `SettingsViewModel` holding preferences, active provider status, IME default status, and toggle states.
* **Flags for Implementation:** Standard Compose `LazyColumn` inside a `Scaffold` with `NavigationBar`.

#### 4.2 Persona Browser
* **Spec Mapping:** Screen 4.2 — "Persona browser"
* **Mockup Folder:** `settings_persona_browser` (Primary)
* **Purpose:** Bento grid gallery to view and select installed personas.
* **Key Components:**
  * *Header:* Outfit 28sp `"Select Persona"`, subtitle text.
  * *2×2 Bento Grid of Cards:* Glass surface `#161B22`, 16dp rounded.
    - `🎩 Jeeves`: Large emoji (40sp), Outfit semibold title, context subtext, teal badge `[DEFAULT]`. Hover/Active glow.
    - `🏛️ Sir Humphrey`: Context `"master of bureaucratic circumlocution"`.
    - `🤠 Dr. Schultz`: Context `"eloquent, courteous bounty hunter"`.
    - `🎬 Bachchan`: Context `"every reply to a full house"`.
  * *Floating Action Button (FAB):* Teal gradient circle with `+` icon: `"+ Add persona"`.
  * *Bottom Nav:* Personas tab active.
* **Navigation:** Tap persona card → Screen 4.3 (`settings_persona_detail`). Tap FAB → Custom persona creation flow.
* **State / Data Implied:** `personas: List<Persona>`, `defaultPersonaId: String`.
* **Flags for Implementation:** Use Compose `LazyVerticalGrid`.

#### 4.3 Persona Detail
* **Spec Mapping:** Screen 4.3 — "Persona detail"
* **Mockup Folder:** `settings_persona_detail` (Primary)
* **Purpose:** Inspects persona speech patterns, key vocabulary, and sample outputs.
* **Key Components:**
  * *Hero Banner:* 40sp emoji `🎩` over radial teal glow, Outfit 28sp `"Jeeves"`, italicized origin quote.
  * *Speech Patterns Section:* 3 trait cards with icons (`gavel`, `bolt`, `lightbulb`): `"Formal"`, `"Efficient"`, `"Resourceful"`.
  * *Vocabulary Section:* Horizontal scrolling list of pill chips (`#14B8A6`/10 background, teal border): `"if I may say so, sir"`, `"I rather fancy that..."`, `"quite so"`.
  * *Sample Lines Card:* Frosted glass panel with quote watermark and 4 sample lines.
  * *Fixed Bottom Bar:* Primary teal button `"Try this persona"`, disabled outlined button `"Set as default"`.
* **Navigation:** Top back icon → Returns to Screen 4.2. `"Try this persona"` → Opens mini test input drawer.
* **State / Data Implied:** Full `PersonaDefinition` model (`id`, `name`, `speechPatterns`, `vocabulary`, `sampleLines`, `isDefault`).
* **Flags for Implementation:** Horizontally scrollable `LazyRow` for vocabulary chips.

#### 4.4 Provider Settings
* **Spec Mapping:** Screen 4.4 — "Provider settings"
* **Mockup Folder:** `settings_ai_providers` (Primary)
* **Purpose:** Manages AI backend selection and API key credentials.
* **Key Components:**
  * *Active Provider Card (Gemini):* Highlighted with solid teal border. Google icon, `"Gemini"`, `"FREE"` badge, active green status dot. Outlined button `"Test connection"`.
  * *Stored Key Row:* Background `#2D333B`, masked dots `••••••••••••••••` (System Monospace), trailing action buttons `"Reveal"` and `"Delete"`. Last validated timestamp.
  * *Other Provider Cards Stack:* Claude, OpenAI, OpenRouter, Local. Each card displays name, short description, and configuration status (`✓ Configured` / `Not set up`). Button `"Setup"` or `"Connect"`.
* **Navigation:** `"Test connection"` → Triggers async HTTP ping. `"Setup"` → Expands key input field.
* **State / Data Implied:** `providerConfigs: Map<ProviderType, ProviderConfig>`, `activeProvider: ProviderType`.
* **Flags for Implementation:** Secured via Android Keystore (`EncryptedSharedPreferences`).

#### 4.6 Privacy Page
* **Spec Mapping:** Screen 4.6 — "Privacy page"
* **Mockup Folder:** `settings_privacy` (Primary)
* **Purpose:** Plain-English breakdown of privacy architecture and zero-logging policy.
* **Key Components:**
  * *Hero Header:* Outfit 28sp `"What we store"`, large teal gradient text `"Nothing."`
  * *Detail Rows (Glass Cards with Icons):*
    - `Your text:` → `"Sent to [your chosen provider] only when you tap Transform. Never stored, never logged."`
    - `Your API key:` → `"In Android Keystore. Encrypted on-device. We cannot read it even if we wanted to."`
    - `Your typing patterns:` → `"Handled by the base keyboard for autocorrect. Never sent anywhere."`
    - `Analytics:` → `"None. No Firebase, no telemetry, no crash reporting phone-home."`
  * *Primary CTA:* Teal gradient button `"Read the code yourself →"`.
  * *Footer Note:* `"If any of the above ever changes, the commit diff is the receipt."`
* **Navigation:** `"Read the code yourself →"` → Opens external browser to `https://github.com/apexcloudwise/personaspeak`.
* **State / Data Implied:** Static view.
* **Flags for Implementation:** Must adhere strictly to load-bearing privacy copy frozen by ADR-0005.

---

### Set 6: Edge Cases & Secondary Flows

#### 6.2 Long Text Result
* **Spec Mapping:** Screen 6.2 — "Long text result"
* **Mockup Folder:** `edge_case_long_text_result_sir_humphrey` (Primary)
* **Purpose:** Demonstrates scrollable height containment when rewritten text is very long.
* **Key Components:**
  * *Scrollable Result Container:* Maximum height capped at `353px` (approx 40% viewport height). Custom scrollbar thumb in teal `#14b8a6`.
  * *Header:* `"🎩 Sir Humphrey • formal mode"`.
  * *Body:* Multi-paragraph elaborate bureaucratic response.
  * *Actions:* Bottom sticky action bar with `"Use this"` and `"Again"`.
  * *Keyboard Send Key:* Soft outer teal glow (`box-shadow: 0 0 20px rgba(79, 219, 200, 0.2)`).
* **Navigation:** Internal vertical scrolling. `"Use this"` commits entire scrollable text.
* **State / Data Implied:** `TransformationResult(text = "...")` exceeding standard card height.
* **Flags for Implementation:** Use Compose `Modifier.heightIn(max = 320.dp)` with `Modifier.verticalScroll()`.

#### 6.4 First Transformation Celebration (Static Banner)
* **Spec Mapping:** Screen 6.4 — "First transform ever — celebration"
* **Mockup Folder:** `edge_case_first_transformation_celebration` (Primary)
* **Purpose:** Welcomes first-time users with a celebratory card accent upon initial transformation.
* **Key Components:**
  * *Top Celebration Banner:* Surface `#14B8A6`/20 inside result card. Star icon (`auto_awesome`), Outfit semibold text `"Your first transformation! 🎉"`.
  * *Particle Effect:* CSS floating particles (`#4fdbc8`, `#03b5d3`, `#71f8e4`) scattered around card.
  * *Card Content:* Standard `"JEEVES · polite"` result text and action buttons.
* **Navigation:** Standard result card actions.
* **State / Data Implied:** `isFirstTransform: Boolean` stored in `SharedPreferences`.
* **Flags for Implementation:** Render particle scatter via lightweight Compose `Canvas` drawing rather than WebGL shaders.

#### 6.4 First Transformation Celebration (Animated Shader Variant)
* **Spec Mapping:** Screen 6.4 (Variant)
* **Mockup Folder:** `edge_case_animated_celebration`
* **Purpose:** Alternative visual exploration using an embedded WebGL GLSL shader.
* **Key Components:** Background GLSL shader (`STITCH_SHADER_START:ANIMATION_25`) rendering moving teal cyan light fields behind a semi-transparent result card.
* **Flags for Implementation:** Deprecated for Slice 5 build in favor of static banner + Compose Canvas due to AGSL API level requirements.

---

## 4. Architectural Summary for Engineering Implementation

| Architectural Layer | Component Responsibilities | Key Files / Modules |
|---|---|---|
| **IME Window UI** | Docked `PersonaStrip`, floating `ResultCard`, `PersonaPickerPopup`, `MoodPickerPopup`, Error popups. | `keyboard` module (`PersonaStrip.kt`, `ResultCard.kt`) |
| **Settings App UI** | Standalone Android `Activity` hosting `SettingsHome`, `PersonaBrowser`, `PersonaDetail`, `ProviderSettings`, `PrivacyPage`. | `app` module (`MainActivity.kt`, `ui/settings/`) |
| **Onboarding App UI** | Standalone setup activity / navigation graph covering `Welcome`, `Setup`, `ProviderSelection`, `ApiKeyInput`, `Demo`. | `app` module (`ui/onboarding/`) |
| **Secure Key Storage** | Hardware-backed key encryption using Android Keystore. | `core-providers` (`KeyStorage.kt`, ADR-0005) |
| **Editor Guard** | Generation token / selection offset snapshot guard preventing stale-field races. | `keyboard` (`EditorIdentityGuard.kt`, ADR-0003) |
