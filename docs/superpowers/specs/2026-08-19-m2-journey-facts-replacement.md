# M2 Journey-Facts Layer Replacement (Stage 5 continuation)

Status: Approved by owner + overseer (Sigrid, ruling of 2026-08-19 in
issue #79). This note is the separately approved replacement
architecture under #47's counted-failure rule.

## What is replaced and what is kept

Kept, unchanged: the outer qualification instrument — phase structure,
capture records and their frozen schema, the command ledger, the
fail-closed boundary, ownership/cleanup, restoration, the fixture
transaction, and the cryptographic signer gate. All of these ran
successfully against the real pinned fixture on 2026-08-19 (run
20260819T123124Z reached install and editor focus before the journey
failed).

Replaced: the journey-facts layer — the mechanism by which the journey
observes the keyboard and drives its taps.

## The three refuted premises (evidence in #79)

1. That `uiautomator dump` can observe the IME. On API 14 (emulator
   36.6.11, pinned fixture) the hierarchy contains zero IME-package
   nodes even while the IME is bound, drawn, and visible. The channel
   is structurally empty; no code change can fill it.
2. That fake hierarchy nodes could stand in for real ones. The fakes
   wrote keyboard nodes into every dump unconditionally, so the
   fake-only matrix certified a journey that cannot observe anything on
   a real device.
3. That stored tap geometry matched the real layout. The real layout
   has a utility row above the letter rows, a Home key at a row's left
   edge, and a submit key in the bottom row; the automated replay of
   the stored 11-tap sequence drifted the device into Google Assistant
   settings.

Also missing entirely: IME enablement and selection. Android will not
show an installed-but-disabled IME. Proven remedy on the fixture:
`ime enable biz.pixelperfectstudios.personaspeak/com.menny.android.anysoftkeyboard.SoftKeyboard`
followed by `ime set` of the same component; the IME then binds
(`mHaveConnection=true mBoundToMethod=true mVisibleBound=true`) and its
InputMethod window is shown and drawn.

## The replacement facts channels

- IME registration/enablement/selection: `ime enable` + `ime set`
  after install, both fail-closed; prior IME state is restored by the
  existing snapshot-load restoration and verified by the existing
  enabled/default IME checks.
- IME binding and visibility: `dumpsys input_method` fields
  (`mCurMethodId`, `mHaveConnection`, `mBoundToMethod`,
  `mVisibleBound`).
- Keyboard window presence/shown: `dumpsys window windows`, the
  InputMethod window owned by the personaspeak uid (`shown=true`,
  `HAS_DRAWN`).
- Typed and applied text: the host app's editor node in the hierarchy
  dump (the Settings search EditText is dump-visible; its `text`
  attribute is the behavioral bridge for typing and apply).
- The candidate/action surface (Rewrite → suggestion → use
  this/dismiss): not observable by any dump channel; observed by
  screenshots plus the owner's visual review seat, bound by digest into
  the capture record and final receipt as already provided by the
  evidence machinery.
- Keyboard geometry: recalibrated against the real layout and pinned;
  special keys (Home, submit, utility row) are avoided or used
  deliberately. Coordinates are derived from the fixture's actual
  layout as captured in the 2026-08-19 debug session screenshots and
  verified by the editor-text bridge before any state transition.

## Post-restore expectation (corrected)

After a snapshot restore to pristine state the Settings search editor
does not exist — the home screen is showing. verify_restore must
assert pristine-state facts (identity properties, IME baseline, package
absence), not the presence of a journey-time editor.

## Fakes become honest

The fake toolkit stops writing keyboard nodes into hierarchies. It
emulates `ime enable`/`ime set`, `dumpsys input_method`, and
`dumpsys window` faithfully, including failure modes, so the
acceptance matrix can actually catch divergences of this class. Matrix
variants and both exact goldens are updated accordingly.

## Count ruling (Sigrid, verbatim intent)

The two failed captures remain permanently recorded as failures of the
retired journey-facts architecture. Once this reviewed replacement
lands, the active count restarts at 0 for the replacement layer; the
next real capture is attempt 1 under the new architecture, not a third
attempt under the rejected one. Narrow reset, not an erasure of
history.

## Source evidence

Issue #79 (packet, ruling, addenda), tracker #69 entries 25–27,
incident archives `~/m2-qual-evidence/20260819T005731Z/` and
`~/m2-qual-evidence/20260819T123124Z/`, debug session artifacts in
`~/m2-probe-20260819/` and `~/m2-qual-evidence/` (owner manual-journey
screenshots owner-journey-{1..4}.png, arch-debug-keyboard.png,
arch-debug-after-taps.png).
