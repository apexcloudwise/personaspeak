# ADR-0010: FlorisBoard as a second, evaluation IME host

**Status:** Accepted (2026-09-02, PR #122 merge) — the second-host
option itself is decided and landed; the addendum below governs the
evaluation. Promoting FlorisBoard to the default host, or deleting this
tree, both remain open decisions gated on the evidence below.

## Context

ADR-0003 picked AnySoftKeyboard as the fork base and rejected FlorisBoard on
three grounds: its suggestion engine was stubbed at v0.5.2, the pinned tag
did not build without off-repo intervention (a purged jetpref snapshot, a
missing Rust toolchain), and the maintainer had publicly described the
project as stalled. The spike did record, though, that FlorisBoard had the
**cheapest graft** of the three candidates (+11/−1 upstream lines, 2 of them
the real seam) and the best stack fit (Kotlin + Compose end to end).

The owner asked for a draft PR that either adds FlorisBoard as an option or
replaces ASK with it, keeping existing functionality intact, judgment
delegated.

Two facts decided the shape of the answer:

1. **The rejection grounds still hold.** As of 2026-09-01 FlorisBoard's
   latest stable is still v0.5.2 (Nov 2025); word suggestions remain slated
   for the unreleased 0.6. Replacing ASK today would ship a keyboard with no
   autocorrect — deleting the mature prediction surface ADR-0003 chose the
   base for, and violating "keep existing functionality intact" in the one
   place it is least negotiable: typing.
2. **The portability claim is now load-bearing and testable.** ADR-0001's
   escape hatch ("`core-personas`/`core-providers` transplant into any host
   unchanged") has never actually been exercised — every host integration
   since the fork lives inside the ASK graft. A second host is the cheapest
   possible proof that the PersonaSpeak layer is host-neutral, and the
   cheapest possible insurance if ASK upstream ever dies (its release cadence
   is ~yearly).

## Decision

**Vendor FlorisBoard v0.5.2 as a second, evaluation IME host. The ASK host
stays the default and the only release path. No replacement happens in this
PR.**

Concretely:

- The snapshot is vendored under `android/florisboard/` on the ADR-0004
  discipline (pinned tag + commit, recorded exclusions, `UPSTREAM.md`,
  hand-kept rent ledger). It keeps **its own Gradle root** — two build
  logics and two AGP pins cannot share one root build without breaking one
  of them, and a shared root would put the ASK path's build at risk, which
  is exactly what this PR promises not to do.
- The host-neutral IME layer (race-guarded `InputConnectionEditorPort`,
  `EditorSessionState`, `ImeViewTreeOwners`, `ResolvingProvider`,
  `PersonaSpeakBrain`, plus a graph factory) is extracted from the ASK tree
  into a new first-party module, `:personaspeak-ime`, which both hosts
  consume. The ASK-side composition and row provider stay in the ASK tree;
  their behavior is unchanged and the full ASK unit suite (1269 tests) and
  APK build stay green.
- The FlorisBoard graft follows the spike's seam: a guarded
  `FlorisPersonaSpeakRow` inserted in `TextInputLayout`'s existing
  `Column { Smartbar(); …; TextKeyboardLayout() }` — the ADR-0007
  dedicated-row contract, host suggestions and keys visible in every state —
  plus one `by lazy` host field and five one-line lifecycle forwarders in
  `FlorisImeService`, a ported settings activity, and the same manifest
  posture the ASK host carries (INTERNET on opt-in, merged backup rules that
  exclude provider credentials, non-exported settings activity).

## Because

- **"Option" is the only reading of the instruction that can guarantee
  intactness.** A replace puts 1,269 green ASK tests, the R8 pass, the
  signing identity, and the release RC behind a rewrite; an additive second
  host leaves the ASK path byte-for-byte untouched except for the module
  extraction its own tests pin.
- **The evaluation is now cheap and reversible.** One `./gradlew` in
  `android/florisboard/` produces an installable APK (`ime set` and go);
  deleting the directory removes the option entirely. The upstream rent is
  seven files, two of them real code (the service forwarders and the row
  insertion).
- **The two known warts are now paid, recorded costs rather than unknowns.**
  The jetpref repoint (purged snapshot → stable 0.3.0, API-compatible,
  verified by full build and on-device run) is one ledger line; the Rust
  toolchain requirement is a documented host prerequisite. Neither recurs
  per-graft.

## Rejected alternatives

- **Replace ASK with FlorisBoard.** Ships a keyboard without suggestions
  (0.6 still unreleased), invalidates the M-milestone qualification work
  pinned to the ASK host, and holds the v0.1.0 release hostage to a base
  swap. Revisit when FlorisBoard ships a real suggestion engine.
- **Ship both hosts in one APK (two IME services).** Doubles the binary,
  doubles the settings surfaces, and makes the privacy story harder to
  explain — a returned-PR offense by house rule. The two-APK, two-root shape
  gets the same comparison value without the product cost.
- **Merge the FlorisBoard modules into the unified android root.** AGP
  8.12/Gradle 9.2.0/Kotlin 2.2.20 (FlorisBoard) against the unified root's
  pins cannot coexist in one build without upgrading the ASK side — a risk
  to the shipping path this PR exists to avoid.

## Consequences

- **The repo now builds two Android apps, and the single-APK gate is scoped
  to match (P5, implemented 2026-09-04).** `verify-single-apk.sh` keeps the
  unified root's law exactly — one APK at the canonical path, one
  application project at `keyboard/ime/app/build.gradle` — and names two
  tolerated artifact classes: `florisboard/**` outputs (the evaluation
  second root's own business) and `*/build/outputs/apk/androidTest/**`
  (first-party library instrumentation APKs — test runners, never
  shippable; the `:personaspeak-ime` ADR-0003 suite produces one on any
  local `connectedAndroidTest` run). A lookalike directory
  (`florisboard-fake/`) or a stray app project under the second root is
  still a finding; the second root may carry at most its one sanctioned
  application project (`florisboard/app/build.gradle.kts`).
- **Session isolation differs by host, deliberately.** The ASK host scopes
  panel state with per-session `ImeViewTreeOwners` (its IME window has no
  Compose owners); the FlorisBoard host rides the window's real
  `LifecycleInputMethodService` owners and re-keys the panel ViewModel on a
  session-generation counter. Both bound panel state to the input session;
  the mechanisms differ because the hosts differ. Promotion review should
  decide whether to unify on one story.
- **Promotion to default requires, at minimum:** FlorisBoard 0.6 shipping a
  real suggestion engine; the ASK-host device journey re-run green on this
  branch's head; the FlorisBoard host passing the same M-milestone gates
  (R8, signing, privacy/egress audits, journey harness) the ASK host passed;
  and a non-author review. Until then this tree is evaluation furniture.
- **The `:personaspeak-ime` extraction is a structural improvement on its
  own.** The module law's purity scans now cover it
  (`verify-milestone-2.sh` scans it for ASK imports and runs its tests), and
  any future host — or the ASK host's own refactor — consumes the same
  graph.

## Addendum (2026-09-02): evaluation governance

Recorded per the review loop on PR #122: an evaluation that is nobody's job
is drift with extra steps. This addendum is the bar the PR must meet to
merge, and the terms on which the second host lives or dies afterwards.

1. **Named owner.** zaphodis42 owns the evaluation decision — whether the
   FlorisBoard host promotes, stays, or goes. The labor can be delegated;
   the call cannot.
2. **Evaluation/revisit date.** 2026-12-01, or the day FlorisBoard 0.6
   ships a stable suggestion/autocorrect engine, whichever comes first.
   At the revisit: promote per the criteria below, extend the date once in
   writing, or delete per the criteria below.
3. **Promotion criteria** (all required; the bar is the ASK host's own
   M-milestone record):
   - A real suggestion/autocorrect engine is functioning — native
     FlorisBoard 0.6, or the ASK prediction surface extracted behind the
     `:personaspeak-ime` seam.
   - Composing-text regression coverage via on-device instrumentation, per
     ADR-0003's rule — unit tests alone do not qualify.
   - Flow/locale parity with the ASK host's required set.
   - Latency and memory parity measured against the ASK host.
   - Full release posture: signing, R8 pass, privacy/egress audit, and a
     CI build job for the second Gradle root.
   - A short blinded UX bake-off against the ASK host.
   - Plus the standing gates from Consequences: the ASK-host device journey
     green on the then-current head, and a non-author review.
4. **Deletion criteria.** The tree goes if any of these holds:
   - The revisit date passes with the promotion criteria unmet and no
     written extension from the owner.
   - The vendored tree needs security or build maintenance that upstream
     will not take and we cannot bound — unbounded rent is not rent, it is
     a second product.
   - The `:personaspeak-ime` extraction graduates into a host that is not
     FlorisBoard, leaving this tree without a reason to exist.

   Deletion mechanics: remove `android/florisboard/` whole — its ledger,
   its Gradle root, and its upstream rent die with the directory — and mark
   this ADR Superseded/Rejected with the reason. `:personaspeak-ime` stays
   regardless: the ASK host consumes it.
