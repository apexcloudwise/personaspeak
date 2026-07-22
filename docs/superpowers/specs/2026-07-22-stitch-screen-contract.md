# PersonaSpeak Stitch screen contract

**Date:** 2026-07-22

**Status:** Owner-approved design

**Tracker:** [issue #39](https://github.com/apexcloudwise/personaspeak/issues/39)

**Authority:** [ADR-0005](../../adr/0005-privacy-posture-fork-audit.md) ·
[ADR-0006](../../adr/0006-gradle-composition-for-the-graft.md) ·
[single-APK design](2026-07-22-single-apk-ask-integration-design.md)

## Purpose

This document turns the preserved Google Stitch prompt and exports into the
screen and state contract for PersonaSpeak. Stitch defines visual intent. The
accepted architecture, Android behavior, editor-safety contract, verified
privacy posture, and accessibility requirements decide what ships when a
mockup conflicts with reality.

PersonaSpeak is one APK built on AnySoftKeyboard (ASK). ASK's `:ime:app` owns
the application, the `InputMethodService`, and the active editor connection.
Onboarding, settings, and PersonaSpeak's IME surfaces belong to the first-party
`:personaspeak-ui` library. The product never asks a user to switch from a
daily-driver keyboard to a keyless PersonaSpeak panel and back.

This is a design-source contract. It does not implement UI and does not make
the untracked transport zip or export directory shipping source material.

## Source inventory

The prompt requests 36 numbered screens across six sets. The export contains
24 rendered screen folders. Those exports represent 20 unique requested IDs:
four provider-picker exports represent screen 1.3, and two celebration exports
represent screen 6.4. Sixteen requested IDs have no dedicated export.

| Set | Requested IDs | Unique IDs represented | IDs without a dedicated export |
|---|---:|---:|---:|
| 1 — onboarding | 5 | 5 | 0 |
| 2 — keyboard | 9 | 4 | 5 |
| 3 — errors | 5 | 4 | 1 |
| 4 — settings | 6 | 5 | 1 |
| 5 — dark variants | 5 | 0 | 5 |
| 6 — edge cases | 6 | 2 | 4 |
| **Total** | **36** | **20** | **16** |

The `shader/` folder is supporting animation source, not a rendered screen.
`personaspeak_design_system/DESIGN.md` is a token reference, not a twenty-fifth
screen.

### Disposition vocabulary

- **Canonical** — the export is the primary visual target, subject to this
  document's global platform, copy, rights, and accessibility rules.
- **Adapted** — the composition is useful, but named behavior or copy must
  change before it can ship.
- **Variant reference** — the ID reuses a canonical component with different
  data, theme, or dimensions; it is not a separate navigation destination.
- **State-only** — the ID is an observable transition or terminal state rather
  than a persistent screen.
- **Rejected** — the requested interaction contradicts the accepted product or
  safety model and must not be implemented.

## Product-wide rules

### One keyboard, one editor

- ASK's real keys remain visible, stable, and usable during PersonaSpeak IME
  states. A PersonaSpeak card may change the IME window height and cause the
  host to relayout, but it must not change the coordinates of ASK's key rows
  while that card is open.
- The permanent persona strip and every picker, loading card, result, and error
  render inside ASK's input-view hierarchy. They require no overlay permission
  and create no focus-taking dialog or popup window.
- The guided onboarding demo uses an Activity-hosted editable field with the
  installed ASK IME active. It uses the real keys, strip, provider/coordinator
  path, `EditorPort`, and host-field replacement path.
- There is no PersonaSpeak-local draft, fake keyboard, alternate commit path,
  panel back button, switch-to/switch-back flow, or keyless PersonaSpeak IME.
  Android's system keyboard chooser remains only an operating-system escape
  hatch.

### Review before replace

Every successful provider response first appears in a result card. The result
card's `Use this` action is the sole approval to attempt host-editor
replacement. There is no immediate-replace preference and no second
confirmation after `Use this`. `Again` captures current editor content in a
new request. `Dismiss` changes no editor text.

Only `AppliedVerified` may be presented as a confirmed replacement. `Stale`,
`WriteRejected`, and `WriteUnconfirmed` remain distinct. `WriteUnconfirmed`
never retries automatically because the first write may already have landed.

### Theme and dimensions

- All Activity and IME surfaces follow the system theme. The dark Stitch
  palette is the primary token reference; the light palette is a complete
  equivalent, not an onboarding-only exception.
- All interactive targets are at least 48dp in each dimension. A smaller visual
  icon may sit inside that target.
- Long result text scrolls inside a body region whose maximum height is
  `min(320dp, 40% of the current IME window height)`. This is the only result
  body height formula. The card header and actions remain fixed.
- Landscape uses compact labels, horizontal persona browsing where needed,
  pinned result actions, and the same 48dp touch bounds. It derives available
  height from the current IME window, never from a fixed percentage of the
  whole display.
- When Android enters fullscreen extract mode, PersonaSpeak rewrite controls
  are enabled only if the real `EditorPort` can preserve the same capture and
  replacement semantics. Otherwise the controls are disabled with the plain
  explanation `Rewriting is unavailable in this full-screen editor.` Normal
  ASK editing remains available.

### Copy and claims

Permission, privacy, key handling, provider cost, and failure explanations are
plain language. Persona voice may decorate loading and recoverable error copy
only after the cause and effect are clear.

- The onboarding reassurance is `Typing works offline`. Supporting text states
  `Cloud rewrites need a connection.` It must not imply that cloud rewriting
  works offline.
- Glide typing is not advertised until ASK's corresponding feature is enabled
  in the shipping configuration and verified on a device. Until then the
  welcome screen uses a verified daily-driver typing capability instead.
- A provider may be described as `Free tier available; account, API key, and
  provider limits apply.` It must not be described as free without those
  qualifications.
- A credential is encrypted using a Keystore-held key and decrypted in the app
  process when authenticating with the selected provider. Copy must not say the
  API key itself lives in Keystore, cannot be read by the app, or is
  hardware-backed unless runtime evidence proves the narrower claim.
- The privacy surface distinguishes local learned words, prediction state,
  dictionaries, clipboard behavior, backup eligibility, diagnostics, and
  provider egress. It never collapses those categories into `Nothing.`
- Final public privacy copy remains gated by ADR-0005's static and on-device
  audit. Before that gate passes, development surfaces identify the copy as a
  development disclosure and do not make unverified absolute claims.

### Assets and licenses

- No remotely hosted raster asset from the exports ships. This includes
  portraits, avatars, backgrounds, textures, and decorative illustrations.
- Persona portraits do not ship until redistribution rights are recorded. Use
  emoji or project-owned placeholders in the meantime.
- Bundled Outfit and Inter font files include their OFL text and required
  notices. If either font is not bundled with compliant notices, the UI falls
  back to system fonts and records the fidelity deviation.
- Bundled Material Symbols include the applicable Apache-2.0 notice. Otherwise
  they are replaced with platform or project-owned icons.
- Asset provenance is a release artifact, not a comment in an implementation
  file.

### Persona identity and future sources

Screens consume personas from the source-neutral repository accepted by the
single-APK design. UI code does not read bundled YAML or branch on whether a
persona is bundled, downloaded, or imported. Persona IDs are stable,
source-qualified opaque values. Routes percent-encode the complete ID rather
than split or interpret it. Display names are never keys.

If an active persona is missing, deleted, or fails validation, the UI selects a
known-valid bundled fallback, announces the change, and shows a non-color-only
settings notice. Add, download, update, trust, signature, moderation, and
marketplace behavior remain out of scope pending their own ADR.

## Activity routes

The routes below live in activities packaged by ASK's sole `:ime:app`. They
share one settings repository and do not imply a second application.

| Route | Surface | Required behavior |
|---|---|---|
| `onboarding/welcome` | Welcome | First entry or explicit restart of onboarding. |
| `onboarding/ime-setup` | Enable and select | Reflect real enabled/default state after every resume. |
| `onboarding/provider` | Provider selection | Show implemented, available providers as selectable; show a known but unavailable provider only when it is disabled with a reason. |
| `onboarding/credential/{providerId}` | Provider credential | Test and store configuration for the selected provider. |
| `onboarding/demo` | Guided demo | Host a real editable field and require the installed ASK IME. |
| `settings/home` | Settings home | Show keyboard status and navigation groups. |
| `settings/personas` | Persona browser | Read the validated repository snapshot. |
| `settings/personas/{personaId}` | Persona detail | Resolve one percent-encoded opaque, source-qualified ID. |
| `settings/providers` | Provider list | Show selected-provider readiness and other availability. |
| `settings/providers/{providerId}` | Provider configuration | Configure, test, replace, or delete that provider's key. |
| `settings/privacy` | Privacy disclosure | Render only audit-supported claims. |
| `settings/appearance` | Appearance | Theme follows system by default; expose only implemented choices. |
| `settings/typing` | ASK typing settings | Enter the inherited ASK settings surface in the same package. |

Android input-method settings and the system input-method picker are system
destinations, not PersonaSpeak routes. Returning from either resumes
`onboarding/ime-setup`, which recomputes status instead of assuming success.

The settings-home status banner has three explicit variants. `Enabled and
default` confirms readiness without an action. `Enabled, not default` offers
the real system picker. `Disabled` offers Android input-method settings. The UI
must derive these states from Android after each resume; it never marks a step
complete because an intent was launched.

### Onboarding progression and recovery

Onboarding has four independently satisfied requirements: IME enabled, IME
selected, provider selected, and selected-provider ready. A provider is ready
according to its own contract; a future local provider may require no key.
Credentials belonging to another provider do not make the selected provider
ready.

`Get started` advances to the first unmet requirement. `Skip setup`, any
provider/key skip, cancellation, process death, and a later relaunch also resume
at the first unmet requirement. Skipping provider setup still leaves ASK fully
usable for ordinary typing. The demo then offers provider setup instead of
simulating a successful production rewrite.

The setup screen launches Android's real settings and chooser surfaces. It
does not reproduce Android's security warning as a fake modal. The Activity may
explain in advance that Android will display its own warning.

## IME render states

The IME surface is a single state machine hosted above unchanged ASK key rows.
Only one expanded PersonaSpeak card or picker is visible at a time.

| State | Content | Actions and transition |
|---|---|---|
| `Resting` | Persona chip, mood chip, rewrite action. | Open either picker or capture a rewrite. |
| `Unavailable.NoProvider` | Muted rewrite affordance and setup action. | Open `settings/providers`; normal typing continues. |
| `Unavailable.FullscreenExtract` | Compact plain explanation. | Dismiss explanation; no rewrite action. |
| `PersonaPicker` | Validated persona list and selected ID. | Select and persist, dismiss, or open `settings/personas`. |
| `MoodPicker` | Supported mood list and selected mood. | Select and persist or dismiss. |
| `Loading` | Persona, mood, skeleton, progress announcement, cancel. | Cancel to `Resting`; success to `Result`; failure to typed error. |
| `Result` | Rewritten candidate with fixed actions. | `Use this`, `Again`, or `Dismiss`. |
| `Applying` | Brief disabled action/progress state while `EditorPort` attempts replacement. | Route only from `Use this`. |
| `AppliedVerified` | No card; host field contains verified replacement. | Return to `Resting`. |
| `Error.EmptyInput` | Compact advisory; no provider call occurred. | Auto-dismiss or dismiss to `Resting`. |
| `Error.NoProvider` | Setup card; no provider call occurred. | Open selected-provider setup or dismiss. |
| `Error.MissingOrInvalidKey` | Selected provider rejected or lacks its credential. | Open that provider's settings or dismiss. |
| `Error.Offline` | Network unavailable; editor unchanged. | Fresh retry or dismiss. |
| `Error.RateLimitedOrQuota` | Provider limit; editor unchanged. | Dismiss or open provider settings when useful. |
| `Error.ProviderFailure` | Sanitized provider failure; editor unchanged. | Fresh retry or dismiss. |
| `Error.MalformedResponse` | Empty or unusable result; editor unchanged. | Fresh retry or dismiss. |
| `Error.StaleEditor` | Original snapshot no longer matches; no mutation was sent. | Fresh capture/rewrite or dismiss. |
| `Error.WriteRejected` | Editor rejected a required command. | Dismiss; no automatic write retry. |
| `Error.WriteUnconfirmed` | A command may have landed but verification failed. | Ask for inspection of the host field; dismiss only. |
| `Error.SensitiveEditor` | Protected editor refused before capture. | Dismiss; no provider traffic. |
| `Error.UnsupportedEditor` | Null, non-text, unreadable, partial, or otherwise unsupported editor. | Dismiss; no provider traffic. |
| `Error.IncompleteRead` | The complete draft could not be captured. | Dismiss; no provider traffic. |
| `Error.OversizedInput` | Draft exceeds 8,000 Unicode code points. | Dismiss; no provider traffic. |

`IncompleteRead` is a specific `UnsupportedEditor` presentation because it can
explain what happened without exposing editor internals. Provider errors,
prompts, credentials, draft text, and raw response bodies never appear in UI
diagnostics or logs.

`Again` and every retry that can contact a provider start with a fresh
`captureSnapshot()`. They never reuse a draft or editor token retained from the
previous request. Cancellation may save provider work, but only commit-time
revalidation authorizes an attempted write.

## Requested-screen disposition

### Set 1 — onboarding

| ID | Requested screen | Disposition | Contract |
|---|---|---|---|
| 1.1 | Welcome | Adapted | Use `onboarding_welcome` composition. Replace the blanket offline and glide claims as specified above; use licensed/project-owned art. |
| 1.2 | Make it your keyboard | Adapted | Use `onboarding_setup` cards, but launch and observe real Android surfaces. Never draw the system warning as product UI. |
| 1.3 | Pick a brain | Canonical | Use `onboarding_ai_selection_minimalist` as the base: full-width 48dp rows, complete provider names, readiness text, and only implemented choices. |
| 1.4 | Your key, your business | Adapted | Use `onboarding_api_key` composition with provider-specific instructions and truthful Keystore/decryption wording. Validation success requires a real provider test. |
| 1.5 | Try it | Adapted | Preserve the visual walkthrough, but use an Activity-hosted editor and the real installed ASK IME. No simulated chat editor or keyboard state machine. |

The standard, high-density, and visual-card provider exports remain visual
references only. The standard variant's descriptive hierarchy may inform
supporting copy, but its rows must meet touch targets. High-density provider
metrics are rejected because they become stale product claims. Branded visual
cards are rejected until brand and raster rights are recorded.

### Set 2 — keyboard in action

| ID | Requested screen | Disposition | Contract |
|---|---|---|---|
| 2.1 | Resting strip | Adapted | `keyboard_resting_state` is the visual base. Mount one permanent row above ASK's inherited suggestion row unless later device evidence supports a separately reviewed merge. |
| 2.2 | Persona picker | Adapted | Use `keyboard_persona_picker` content in the IME view, not a dialog/popup window. Long names wrap or ellipsize without losing the selected state. |
| 2.3 | Mood picker | Adapted | Use `keyboard_mood_picker` as an in-view anchored card with 48dp rows. |
| 2.4 | Loading | Adapted | No export. Reuse the result-card bounds with skeleton/progress, a TalkBack live-region announcement, and a functional `Cancel`. The draft remains in the host field. |
| 2.5 | Result | Adapted | Use `keyboard_result_jeeves`; retain `Use this`, `Again`, and dismiss. The original host text remains unchanged until `Use this`. |
| 2.6 | After use | State-only | Only `AppliedVerified` dismisses the card and returns to `Resting`. The host app owns its send action. |
| 2.7 | Dr. Schultz result | Variant reference | Render `Result` with the selected persona's validated data; do not hard-code an export sample as provider output. |
| 2.8 | Sir Humphrey result | Variant reference | Same component and rule as 2.7. |
| 2.9 | Bachchan result | Variant reference | Same component and rule as 2.7. |

### Set 3 — errors

| ID | Requested screen | Disposition | Contract |
|---|---|---|---|
| 3.1 | No connection | Canonical | Use `error_no_connection`; retain amber advisory styling, explicit untouched-editor copy, fresh retry, and dismiss. |
| 3.2 | Invalid API key | Adapted | Use `error_invalid_api_key`; route `Open settings` to the selected provider, not a generic credential screen. |
| 3.3 | Quota exhausted | Adapted | Use `error_quota_exhausted`, but remove decorative `Try again tomorrow`. The provider may not define a daily reset. Offer only truthful, functional actions. |
| 3.4 | Empty or malformed response | Adapted | No export. Reuse the amber error card as `Error.MalformedResponse`; expose no raw response. |
| 3.5 | No provider | Adapted | Use `error_no_provider_configured`. Read selected-provider readiness, not the presence of any stored credential. |

The same amber card family adapts to `ProviderFailure`, `StaleEditor`,
`WriteRejected`, `WriteUnconfirmed`, `SensitiveEditor`, `UnsupportedEditor`,
`IncompleteRead`, and `OversizedInput`. Their titles, explanations, and action
sets remain distinct because their recovery and mutation guarantees differ.

### Set 4 — settings

| ID | Requested screen | Disposition | Contract |
|---|---|---|---|
| 4.1 | Settings home | Adapted | Use `settings_home`. Remove rewrite-behavior configuration and unsupported usage counters. State `Review before replacing` as fixed behavior. Render enabled/default, enabled/not-default, and disabled IME banners. |
| 4.2 | Persona browser | Adapted | Use `settings_persona_browser` with repository-backed IDs. Hide `Add persona` until a later import/marketplace design supplies a real destination. |
| 4.3 | Persona detail | Adapted | Use `settings_persona_detail`. `Set as default` persists the opaque ID. Replace the local mini test field with `Try on keyboard`, which opens the real guided-demo editor or explains how to use the active IME. |
| 4.4 | Provider settings | Adapted | Use `settings_ai_providers` for selected-provider configuration and tests. Remove automatic fallback/retry controls and unsupported model metrics. Key status is provider-scoped. |
| 4.5 | Rewrite behavior | Rejected | Immediate replacement, shake-to-undo, and back-to-undo do not ship. `Use this` is the fixed approval boundary. |
| 4.6 | Privacy | Adapted | Use `settings_privacy` layout only. Replace every absolute claim with the separated, audit-supported disclosures in this contract and ADR-0005. |

### Set 5 — dark variants

| ID | Requested screen | Disposition | Contract |
|---|---|---|---|
| 5.1 | Dark resting strip | Variant reference | Dark-theme evidence for 2.1; selected state must remain visible without color alone. |
| 5.2 | Dark result | Variant reference | Dark-theme evidence for 2.5 and the long-card formula. |
| 5.3 | Dark persona picker | Variant reference | Dark-theme evidence for 2.2, including tile boundaries and long names. |
| 5.4 | Dark error | Variant reference | Dark-theme evidence for the complete error family, not only offline. |
| 5.5 | Dark settings | Variant reference | Dark-theme evidence for all Activity routes. |

Dark mode is not a separate navigation graph. Follow-system theme applies to
onboarding, settings, demo, and IME surfaces.

### Set 6 — edge and secondary states

| ID | Requested screen | Disposition | Contract |
|---|---|---|---|
| 6.1 | Empty input | State-only | Show a compact accessible `Error.EmptyInput`; do not contact a provider. Auto-dismiss must pause while accessibility services are reading it. |
| 6.2 | Long result | Canonical | Use `edge_case_long_text_result_sir_humphrey` with the single result-body formula, fixed header/actions, and internal scrolling. |
| 6.3 | Replacement confirmation | Rejected | `Use this` already expresses approval. No redundant confirmation, original-text duplicate, or `Keep mine` dialog. |
| 6.4 | First transformation celebration | Rejected for initial flow | Both celebration exports are retained as references only. Celebration is deferred until the core journey passes accessibility and performance gates. It must respect reduced motion and never delay typing if later approved. |
| 6.5 | Keyboard without provider | Adapted | Render `Unavailable.NoProvider` while all ASK typing features remain usable. Setup opens the selected-provider settings route. |
| 6.6 | Landscape | Adapted | Apply the landscape and height rules in this contract to every relevant IME state. |

## Export disposition

Every rendered export has an explicit fate. No export is silently treated as
implementation truth.

| Export folder | Requested ID | Use |
|---|---|---|
| `onboarding_welcome` | 1.1 | Adapted visual base. |
| `onboarding_setup` | 1.2 | Adapted visual base; fake system dialog excluded. |
| `onboarding_ai_selection` | 1.3 | Supporting-copy reference. |
| `onboarding_ai_selection_high_density` | 1.3 | Rejected metrics variant. |
| `onboarding_ai_selection_minimalist` | 1.3 | Canonical layout base. |
| `onboarding_ai_selection_visual_cards` | 1.3 | Rejected branded-raster variant. |
| `onboarding_api_key` | 1.4 | Adapted visual base. |
| `onboarding_demo` | 1.5 | Adapted visual base; simulated behavior excluded. |
| `keyboard_resting_state` | 2.1 | Adapted visual base. |
| `keyboard_persona_picker` | 2.2 | Adapted visual base. |
| `keyboard_mood_picker` | 2.3 | Adapted visual base. |
| `keyboard_result_jeeves` | 2.5 | Adapted visual base. |
| `error_no_connection` | 3.1 | Canonical visual base. |
| `error_invalid_api_key` | 3.2 | Adapted visual base. |
| `error_quota_exhausted` | 3.3 | Adapted visual base; nonfunctional action excluded. |
| `error_no_provider_configured` | 3.5 | Adapted visual base. |
| `settings_home` | 4.1 | Adapted visual base. |
| `settings_persona_browser` | 4.2 | Adapted visual base. |
| `settings_persona_detail` | 4.3 | Adapted visual base. |
| `settings_ai_providers` | 4.4 | Adapted visual base. |
| `settings_privacy` | 4.6 | Layout reference only until privacy audit. |
| `edge_case_long_text_result_sir_humphrey` | 6.2 | Canonical visual base with normalized height rule. |
| `edge_case_first_transformation_celebration` | 6.4 | Deferred reference; not in initial flow. |
| `edge_case_animated_celebration` | 6.4 | Deferred reference; shader implementation rejected. |

## Accessibility contract

Every shipped state satisfies all applicable requirements below:

- TalkBack announces route titles, selected persona/mood/provider, loading,
  cancellation, result availability, and each failure through deliberate
  semantics and live regions. It never reads skeleton bars as content.
- Switch Access and keyboard traversal can reach every action in a stable,
  logical order. Dismiss icons have text labels.
- Text remains usable at Android's 200% font scale. Content scrolls; actions do
  not overlap or disappear. Long persona and provider names are included in
  fixtures.
- Selection, readiness, success, warnings, and errors use labels/icons in
  addition to color. Contrast is verified in both system themes.
- Reduced-motion settings suppress shimmer translation, springs, particles,
  and decorative movement while retaining a perceivable progress indicator.
- Touch targets remain at least 48dp in portrait, small portrait, and
  landscape. IME compactness is not an exemption.
- Accessibility announcements never include draft text, credentials, or raw
  failures. Provider response text is announced only when the result body
  receives accessibility focus.

## Evidence contract

Activity previews and component screenshot tests prove pixels. They do not
prove IME behavior. Real ASK plus real host editors prove the product path.

### Per-state visual matrix

Each canonical or adapted Activity route and IME state receives:

1. a deterministic component screenshot in follow-system light and dark themes;
2. a small-portrait capture at 200% font scale;
3. landscape coverage where the state can appear in landscape;
4. a side-by-side comparison against its named export or adapted base;
5. a recorded deviation note for copy, assets, Android system UI, or safety
   behavior changed by this contract.

Variant-reference IDs are covered by parameterized screenshots of the shared
component. State-only IDs are covered by state-transition tests and a terminal
capture. Rejected IDs require no implementation screenshot; acceptance checks
assert that their routes, settings, and actions do not exist.

### Emulator journeys

The following journeys are release evidence, not optional demonstrations:

- Fresh install: welcome, real Android enable/select surfaces, provider setup,
  real-ASK guided demo, `Use this`, verified host-field replacement, settings.
- Partial setup: cancel or kill the process after each onboarding step, relaunch,
  and resume at the first unmet requirement.
- No provider: type normally with ASK, invoke setup from the strip, return with
  selected-provider readiness reflected correctly.
- External host: type with ASK in at least two host apps, rewrite, review, apply
  once, and dismiss without mutation.
- Editor safety: move focus, type during a request, invalidate a connection,
  exercise sensitive, unsupported, incomplete, and oversized capture, and show
  that detected stale states send no mutation.
- Write outcomes: reproduce verified, rejected, and unconfirmed results;
  confirm the latter two do not auto-retry.
- Layout: portrait, small portrait, landscape, follow-system themes, 200% font,
  TalkBack, Switch Access, and reduced motion.
- Fullscreen extract: prove equivalent safe behavior or prove the rewrite
  controls are disabled with the specified explanation.

The emulator recording shows ASK key presses and host-editor contents before
and after each editor operation. A local Compose text field that bypasses the
installed IME does not satisfy this evidence.

## PR #22 review disposition

PR #22 supplied useful inventory work but is not an implementation authority.
Its five actionable review findings are resolved one-to-one:

| Finding | Resolution in this contract |
|---|---|
| Conflicting long-result limits (`353px`, 40%, and `320dp`) | One rule: result body maximum `min(320dp, 40% of the current IME window height)`. |
| Absolute privacy claims conflict with ADR-0005 | Privacy copy is separated by data category and remains audit-gated. `Nothing.` is rejected. |
| No-provider detection looked for any stored key | Readiness belongs to the selected provider; local providers may require no key. |
| Coverage incorrectly reported 12 unmocked screens | The inventory records 16: five in set 2, one in set 3, one in set 4, five in set 5, and four in set 6. |
| Key storage was called hardware-backed | Copy says encryption uses a Keystore-held key; hardware-backed is prohibited without runtime proof. |

This contract also corrects PR #22's stale simulated-demo, old module ownership,
focus-taking popup, immediate-replace, and direct credential-storage guidance.

## Acceptance

The Stitch contract is satisfied only when:

- all 36 requested IDs have the disposition and evidence described here;
- all 24 exports are accounted for without shipping the export directory;
- onboarding and settings are routes in ASK's one application package;
- the guided demo and external-host journeys use the real installed ASK IME;
- every rewrite is reviewed before replacement and every write outcome is
  reported honestly;
- no switcher, keyless panel, local IME draft, panel back button, or second APK
  exists;
- selected-provider readiness, process-death recovery, missing personas, themes,
  landscape, fullscreen extract, and accessibility states are exercised;
- privacy copy matches completed audit evidence;
- every bundled font, icon, raster, and portrait has recorded redistribution
  rights and required notices;
- CI, device evidence, patch notes, and a non-author review meet the repository
  Definition of Done.
