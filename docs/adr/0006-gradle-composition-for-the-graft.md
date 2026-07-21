# ADR-0006: Ship one ASK-based PersonaSpeak APK from one Gradle build

**Status:** Accepted (owner decision, 2026-07-22)

## Context

[ADR-0003](0003-fork-anysoftkeyboard-apache.md) selects AnySoftKeyboard (ASK)
as PersonaSpeak's keyboard base. [ADR-0004](0004-vendored-snapshot-ingestion.md)
defines how the upstream snapshot enters the repository. Neither decision says
how ASK's application, PersonaSpeak's pure Kotlin modules, and the existing
onboarding/settings application compose into the product users install.

The first vendoring attempt used `includeBuild("keyboard")`. That kept ASK
isolated, but it did not establish the required `:ime:app -> core-*` dependency,
and it removed the only working IME from the root application. A later
toolchain experiment, recorded in
[PR #23](https://github.com/apexcloudwise/personaspeak/pull/23) at report commit
`18d4cca`, proved that the root modules compile under ASK's Gradle 9.2.1, AGP
8.13.2, Kotlin 2.3.10, and JDK 21 toolchain. It did not prove the unified module
graph or the runtime editor seam.

The earlier thin-IME product model is already superseded. PersonaSpeak must be
one full daily-driver keyboard, not a second keyless panel that users switch to
and back from.

## Decision

PersonaSpeak ships exactly one Android application: ASK's `:ime:app`.

- `:ime:app` owns the application ID, launcher/settings entry points, and
  `InputMethodService`.
- The repository uses one unified Gradle build. ASK modules and PersonaSpeak's
  modules are included directly under the root wrapper.
- The root settings file is authoritative. ASK keeps its logical project paths
  (`:ime:*`, `:addons:*`, and `:api`) while those projects map to the vendored
  directories under `android/keyboard/`; the graft does not rename every ASK
  dependency to manufacture a new hierarchy. The product graph includes ASK's
  required library/pack dependency closure, not its standalone add-on APK
  projects; `:ime:app` is the graph's only Android application target.
- The root adopts ASK's proven toolchain baseline. ASK's `buildSrc`, version
  catalog, rooted scripts, and project paths are reconciled into root-owned
  build logic, one catalog, and one wrapper. Nested ASK build entry points are
  retained as upstream source material, not executed as a second product build.
  The experiment's successful version bump is evidence, not the integration.
- `core-personas` and `core-providers` remain pure Kotlin and are direct
  dependencies of first-party PersonaSpeak Android code.
- Onboarding, settings, theme, resources, persona controls, and result/error
  surfaces live in the first-party `:personaspeak-ui` Android library. It does
  not import ASK implementation types.
- A narrow `EditorPort` interface belongs to first-party code. A thin adapter
  in ASK's application implements it with the real `InputConnection` and
  editor lifecycle. Capture can fail when an editor is sensitive, unreadable,
  partial, or otherwise unsafe. Replacement revalidates immediately before the
  write, uses the least-destructive available command sequence, and reports
  stale, rejected, and unconfirmed outcomes separately. Android provides no
  generic atomic compare-and-set across an IME and a host editor, so the guard
  closes the provider-await race but cannot promise strict atomicity against a
  host mutation in the final cross-process read/write window. Settings
  navigation is not an editor operation and does not travel through this port.
- PersonaSpeak-specific ASK additions live in clearly separated
  `biz.pixelperfectstudios.personaspeak.*` packages. Every modified upstream
  file is recorded in `android/keyboard/UPSTREAM-MODIFIED.md`.
- ASK's input-view hierarchy is a second narrow integration seam. It hosts the
  Compose strip and cards, installs lifecycle, saved-state, and view-model
  owners on the IME window decor view, and keeps pickers inside the input view
  instead of creating a focus-taking dialog window.

The unified graph is accepted only when a device build proves that the ASK APK
installs, registers as an IME, launches PersonaSpeak settings, calls `core-*`,
and performs capture-to-commit through the real host field.

## Explicitly rejected product model

The superseded switcher workflow does not return:

- no second PersonaSpeak keyboard;
- no flip from Gboard to a keyless rewrite panel;
- no automatic flip-back;
- no PersonaSpeak `keyboard back` button;
- no local draft field pretending to be the host editor.

Android may continue to expose its system keyboard chooser. That operating-
system escape hatch is not a PersonaSpeak workflow.

## Consequences

- The current ADR-0001 keyless panel is rejected, non-typing scaffolding — not a
  demoable keyboard. Vendoring moves it byte-for-byte to temporary module
  `:keyboard-stub` at `android/keyboard-stub/` solely to preserve the root APK's
  build, install, and IME-registration baseline while the inert ASK tree occupies
  `android/keyboard/`. No slice may improve it, present its switcher/local-draft
  flow as product behavior, or use it as typing evidence. The proven ASK
  integration deletes it.
- The current root `:app` is not a second shipping APK. Its reusable Compose
  code moves into the first-party Android library; its application role ends.
- The old keyless-panel PR is not merged, and no source from the temporary stub
  is ported as behavior. Independently reusable visual components may be
  reimplemented in the real ASK host only after removing the local draft field,
  switch-back behavior, and every dependency on the rejected topology.
- Upstream upgrades pay rent only at the recorded editor, view-host, and build
  seams.
- A crash in the IME can prevent typing, so install/registration, typing, and
  crash-free startup are release gates.

## Rejected alternatives

### Keep separate builds with composite substitution

This retains coordinate indirection without retaining real toolchain
isolation: a composite invocation still executes under one wrapper. It also
makes the central dependency less obvious than a direct project dependency.

### Put all PersonaSpeak code directly in ASK

This works but scatters first-party behavior through inherited files, enlarges
the upstream rent ledger, and makes future ASK imports unnecessarily theatrical.

### Keep the root app and ASK app

Two installable applications create ambiguous ownership of onboarding,
settings, keys, provider state, and the IME. The product has one package and one
source of truth.
