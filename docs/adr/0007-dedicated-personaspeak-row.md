# ADR-0007: Give PersonaSpeak its own row above ASK suggestions

**Status:** Accepted (owner decision, 2026-07-24)

## Context

[ADR-0003](0003-fork-anysoftkeyboard-apache.md) chose AnySoftKeyboard partly
because its suggestion engine is mature; FlorisBoard's engine was stubbed.
The accepted
[Stitch screen contract](../superpowers/specs/2026-07-22-stitch-screen-contract.md)
therefore mounts one permanent PersonaSpeak row above ASK's inherited
suggestion row and keeps ASK's real keys visible and usable.

Milestone 2 Task 5 drifted from that contract. It mounted a full-width,
content-height Compose surface through ASK's `addStripAction` API. That API
places actions over the candidate row and budgets only the candidate-strip
height. Behavior-neutral device probes at
`e1861837763961515026c7b7c83662c35cf5b8b5` confirmed that the controls render,
but the 1080×148 px PersonaSpeak view overlaps the 1080×95 px candidate view
and extends 53 px into the keyboard.

The design review compared a capped in-strip result, a floating result over
keys, a temporarily expanding strip, replacing suggestions, and a separate
row. Every shared-space option borrowed room from either suggestions or keys.
The owner selected the separate row.

## Decision

PersonaSpeak gets a dedicated, separately measured row above ASK's candidate
row. ASK suggestions and key rows remain visible, usable, and uncovered in
every PersonaSpeak state.

The row lives inside ASK's input-view hierarchy. It uses a narrow generic
extension-row seam in `KeyboardViewContainerView`, not `addStripAction`, an
overlay, a popup window, or a second IME. The row may expand the IME upward for
Review and typed error content. It may not overlap the candidate row or keys.

The existing first-party state machine, `EditorPort`, lifecycle owners,
review-before-replace flow, and request-scoped privacy boundary remain
unchanged. The detailed geometry, flow, recovery, tests, and mockup record live
in the
[dedicated-row design](../superpowers/specs/2026-07-24-dedicated-personaspeak-row-design.md).

## Because

- Suggestions are product behavior, not spare pixels. Hiding them gives back
  one of ASK's main advantages over FlorisBoard.
- Review needs readable candidate text and full-size actions. A 36–40dp strip
  cannot provide both while retaining visible suggestions.
- Floating Review over keys preserves suggestions by making the keyboard stop
  being a keyboard. This is not an improvement.
- A normal measured row matches the already accepted screen contract and
  Android IME hierarchy. It is the only option that preserves both inherited
  surfaces without z-order tricks.
- One generic upstream seam is explicit rent. Reusing an incompatible seam and
  debugging the geometry later is rent with a novelty invoice.

## Rejected alternatives

### Capped in-strip Review

Rejected because candidate text truncates, actions become cramped, and
suggestion content disappears during Review even when the strip height stays
constant.

### Floating Review over key rows

Rejected because it occludes and disables normal typing while Review is open.

### Temporarily expand over suggestions

Rejected because it hides candidate content and repeats the overlap class
confirmed on the emulator.

### Replace ASK suggestions with PersonaSpeak

Rejected because it removes the mature prediction surface that helped decide
the fork base in ADR-0003.

## Consequences

- The IME is permanently taller by the resting PersonaSpeak row. Review and
  error content may expand it further within the accepted sampled-height cap.
- `KeyboardViewContainerView.java` gains a small generic extension-row API and
  matching measurement/layout tests. The edit is recorded in
  `android/keyboard/UPSTREAM-MODIFIED.md`.
- Existing ASK strip actions continue to share the candidate row unchanged.
- The Task 5/6 attachment assumption and Task 7 device gate require a reviewed
  plan amendment. Tasks 8–10 remain blocked until the corrected Task 7 gate
  passes from a final clean head.
- Device acceptance must prove visible suggestions, usable keys, non-overlap
  across all proof-surface states, exact restoration, and privacy-safe raw
  evidence.
