# M2 device-qualification orchestrator design

**Date:** 2026-08-06

**Status:** Draft — two-PR consolidation approved; written-spec review pending

**Milestone authority:** [issue #47](https://github.com/apexcloudwise/personaspeak/issues/47)

**Retrospective:**
[M2 evidence-instrument retrospective](2026-08-06-m2-evidence-instrument-retrospective.md)

## Outcome

Finish Milestone 2 qualification without changing the merged keyboard product.
A small Python orchestrator qualifies the exact APK from `main`, proves the
real keyboard journey on a pristine snapshot-backed AVD, restores the fixture,
and emits a machine-readable receipt. Raw logs and visual media are published
outside `main` and linked from the pull request by immutable commit and digest.

The follow-up proves the keyboard, not the inspector. If review is dominated by
the inspector again, the design has failed even when its test counter is green.

## Non-goals

- No PersonaSpeak product, ASK, Gradle, provider, or UI behavior changes.
- No repair of the retired shell instrument.
- No real provider, credential, onboarding, release, or distribution work.
- No large logs, screenshots, or video in `main` history.
- No device mutation during the machinery pull request.
- No claim that timestamped raw evidence is byte-identical across runs.

## Delivery shape: two pull requests and one preparation lease

Issue #47 remains the parent milestone. The first pull request owns the complete
device-free instrument. A separately reviewed operational lease prepares the
fixture and archive controls without changing repository code. The second pull
request owns only real-device qualification and the compact receipt. Roles are
selected per pull request from actual authorship; they are not permanent
offices with nameplates.

### PR A — complete device-free qualification machinery

Build the typed record model, deterministic JSON codec, local process runner,
remote-result interface, toolchain identity model, and restoration state
machine. Connect the real CLI to adversarial fake `adb`, `emulator`, and
Android tooling. Implement the complete capture-to-private-archive flow,
privacy scan, media validation, byte-bound visual approval, finalize flow, and
compact receipt.

The actual CLI runs end to end through every fake journey and failure path. No
real `adb`, emulator, APK install, IME change, product journey, or device
mutation is permitted outside the separately authorized, confirmation-only
capability probe described below.

The owner-selected roles are Seraph as implementer and Cassie as independent
reviewer.

### Preparation lease — pristine fixture and archive controls

After PR A merges, a separately reviewed non-destructive lease creates and pins
the pristine snapshot-backed clone, cold-boots it twice, proves target-package
absence and every required identity at snapshot creation, restores the source
and clone, and proves force-push/deletion protection for the `evidence`
branch. This is issue #56. It is an operational prerequisite, not a product or
machinery pull request.

### PR B — real-device qualification and compact receipt

Run the already-merged orchestrator against the exact APK from merged `main`.
This PR contains only the compact receipt/index and truthful patch note. Raw
logs and approved media live on the append-only evidence branch. If the real
run discovers an instrument defect, qualification stops; the correction lands
in a separate implementation PR before a fresh attempt.

PR B closes #47 only after the final clean-HEAD host rerun, hosted CI, device
receipt, restoration proof, visual approval, and non-author review all pass on
the exact candidate head.

## Production structure and complexity budget

The implementation lives under `android/scripts/m2_device/`:

| File | One responsibility |
|---|---|
| `records.py` | Immutable result/state types and deterministic JSON codec |
| `commands.py` | Local process execution and the remote-result interface |
| `orchestrator.py` | Phase ordering, stop causes, prior-state capture, restoration |
| `adb_harness.py` | Pinned fixture configuration, device automation commands, and journey execution |
| `evidence.py` | Digests, privacy checks, media validation, manifests, finalize |
| `cli.py` | Argument parsing and `capture` / `finalize` entry points |

Tests mirror those modules under `android/scripts/tests/m2_device/`. The
production package is limited to these six modules and 1,800 nonblank,
non-comment lines in total. `adb_harness.py` is individually capped at 650 lines.
Crossing either limit is an architecture stop that requires an owner-reviewed
design amendment. Tests and fixtures do not count toward the line budget; the
budget prevents the inspector from becoming the largest exhibit in its own case.

Python's standard library is sufficient. No new runtime dependency is added.
Shell may provide a trivial launcher only; it may not orchestrate phases,
parse statuses, restore state, or write evidence records.

## Typed records

`records.py` defines a closed dispatch table. Every type carries `schema=1`
and a `kind` discriminator:

- `ToolIdentity`: logical name, resolved path, version text, and digest where
  the executable is a file.
- `CommandResult`: argv, UTC start/end, wrapper return code, stdout bytes,
  stderr bytes, and timeout/signal disposition.
- `RemoteResult`: transport result plus an optional remote return code. An
  absent remote code is `unavailable`, never inferred as zero.
- `PriorDeviceState`: emulator initial state, package presence/hash, enabled
  IMEs, default IME, serial, fingerprint, API level, and screen geometry.
- `StepRecord`: phase, exact operation, input/output digests, result reference,
  and one terminal cause from a closed enum.
- `CaptureRecord`: exact repository/APK identity, tool identities, ordered
  steps, prior state, restoration result, artifact manifest digest, and
  `visual_review=pending`.
- `ApprovalRecord`: reviewer identity, capture-record digest, exact visual
  manifest digest, decision, and UTC time.
- `FinalReceipt`: capture and approval digests, privacy/media results,
  restoration verdict, mechanical counts, external evidence commit, and
  artifact URLs/digests.

Binary command output is encoded as standard base64 in JSON. The codec rejects
unknown kinds, missing fields, duplicate keys, invalid enum values, malformed
base64, non-UTF-8 text fields, and unsupported schema versions. Serialization
uses sorted keys and fixed separators so the same in-memory record produces
the same bytes. Runtime timestamps remain runtime data; semantic replay is
deterministic, not the clock.

Every dispatch-table variant must encode, decode, and byte-round-trip as a
suite stop condition. Fixtures include empty output, output without a trailing
newline, and output containing `:`, `;`, spaces, and the retired `__RC=` text.
The string is now ordinary payload, where it belongs.

## Command and remote-status boundary

Local execution uses `subprocess.run` or `subprocess.Popen` with argv arrays,
never a shell command string. stdout, stderr, wrapper status, timeout, and
signal are separate fields.

No status marker is appended to remote stdout or stderr. `RemoteResult` is an
interface until a behavior-neutral capability lease proves the exact installed
`adb` mechanism and distinguishes transport status from remote-command status.
That lease may attach to any exclusively owned available emulator, including
`CityZen_Dev`, because it proves installed-tool semantics rather than fixture
state. It records host-tool identities and the observed serial, restores the
emulator's initial running/stopped state, and observes harmless commands only.
It may not install or remove a package, change an IME or setting, run the
product journey, claim qualification/fixture evidence, or select a correction.
It does not depend on issue #56. Its accepted evidence becomes an input to the
later PR A adapter lease. Confirmation and mechanism selection do not share a
lease.

If the installed tooling cannot provide a trustworthy remote status, PR A must
stop for a design amendment. It may not smuggle the sentinel back in wearing a
different hat.

## Toolchain preflight

Before capture authority is consumed, the orchestrator resolves and records:

- Python executable and `python3 --version`;
- JDK path/version from the repository's Java 21 requirement;
- `adb` and `emulator` paths/versions from the Android SDK;
- Build Tools version from both repository version catalogs;
- exact `apksigner` path under that Build Tools version;
- repository HEAD, tracked-clean state, canonical APK path and SHA-256; and
- evidence root, which must be outside the repository and outside a temporary
  directory subject to automatic deletion.

Conflicting repository pins, missing executables, version drift, dirty tracked
state, APK drift, or an evidence root inside the repository stops before any
device process starts. Preflight failures do not consume capture authority.

## Device fixture and phase order

The accepted fixture is a named, snapshot-backed clone derived from
`CityZen_Dev`; the known-dirty source state is not accepted as the baseline.
Issue #56 owns the separately reviewed, non-destructive preparation lease. At
snapshot creation it proves target-package absence, serial policy, system-image
identity, fingerprint, API level, screen geometry, locale, animation settings,
and boot-complete state. It cold-boots twice from the named snapshot and
records the exact AVD name and snapshot digest. Any mismatch or inability to
prove that the source AVD was preserved stops before qualification.

The orchestrator enforces this order:

1. Complete host/toolchain preflight.
2. Prove exclusive emulator ownership and launch the pinned snapshot.
3. Attach the expected serial.
4. Capture `PriorDeviceState` as the first substantive post-attach transaction.
5. Validate serial, fingerprint, API level, screen geometry, and target package
   absence. A mismatch stops before mutation.
6. Install and select the exact APK.
7. Execute the synthetic happy path and negative mutation paths.
8. Capture private logs, hierarchies, screenshots, and video.
9. Restore package, IME, and emulator state in `finally` handling.
10. Re-read state after restore and compare it with `PriorDeviceState`.
11. Emit `CaptureRecord` with `visual_review=pending`; do not finalize.

If prior-state capture fails, the record says `prior_state=unavailable` and the
run stops before mutation. `restoration=MISMATCH` is reserved for a known prior
state that differs after restoration. “We did not look” is not a synonym for
“we failed to restore.”

## Keyboard journey

The real-device journey retains issue #47's product requirements:

- install the exact APK and prove package, version, signer, IME registration,
  enablement, and selection;
- use real ASK key taps to type synthetic text into an external editor;
- show Idle, Loading/cancel, Review, Applied/verified, Dismiss, and stale paths;
- prove Dismiss and stale paths make zero mutations and Apply makes exactly one
  mutation with no retry;
- keep ASK suggestions and all key rows visible and operable;
- launch settings inside the sole PersonaSpeak package;
- record zero fatal exceptions, ANRs, or process deaths; and
- restore and re-verify the exact prior state.

Screenshots and video support review. UI hierarchy, editor-state reads,
structured command results, and mechanical counts supply verdicts.

## Private capture, approval, and finalize

Capture writes to a fresh run directory under the private evidence root. A run
directory is never reused. Raw logs and visual files are immutable after the
capture record and manifest are written.

The reviewer privately inspects the exact manifest bytes. Approval creates an
`ApprovalRecord` bound to both the capture-record digest and visual-manifest
digest. Finalize recomputes both digests, reruns privacy and structural media
validation, and refuses any mutation since approval.

Approved raw evidence is published to an append-only orphan `evidence` branch
under `issue-47/<run-id>/`. Every attempt has a unique run ID; the branch is
never force-pushed. The PR comment links immutable blob URLs containing the
evidence commit, never a moving branch URL.

Issue #56 must prove that branch protection blocks force-push and deletion
before qualification authority. PR B proves that its evidence commit is an
ancestor of the protected branch head and that previously published run IDs
retain their original tree identities.

`main` receives only:

- `docs/evidence/milestone-2/README.md` — compact criterion-to-receipt index;
- `docs/evidence/milestone-2/receipt.json` — `FinalReceipt`; and
- a truthful `PATCHNOTES.md` entry.

No MP4, screenshot, raw log, XML archive, APK, or rejected artifact enters
`main`. Sensitive or rejected files remain private; only their mechanical
count is recorded.

## Required tests

### PR A — complete machinery

- Every dispatch-table record type encodes, decodes, and byte-round-trips.
- Unknown/malformed record variants fail closed.
- Command results preserve empty output, no-final-newline output, arbitrary
  punctuation, nonzero statuses, timeouts, and signals.
- Remote status `unavailable` cannot be treated as zero.
- Orchestrator state transitions reject mutation before prior-state capture.
- Every partial phase invokes restoration exactly once and records the exact
  terminal cause.
- Tool identities reject missing, conflicting, or drifted versions.
- The real CLI runs end to end against adversarial fake executables.
- A coverage matrix enumerates every production command-runner call site
  crossed with every result variant it accepts. Each nonzero-capable site is
  exercised by a genuine nonzero command. A site is exempt only when mechanical
  evidence proves that every status decision delegates to one exhaustively
  tested total dispatch.
- Exact terminal-cause assertions distinguish neighboring failure modes.
- SIGINT, SIGTERM, timeout, child failure, cleanup failure, and partial restore
  all stop and preserve a decodable record.
- Prior-state capture is mechanically first after attach.
- Evidence-root guards reject repository and temporary paths.
- Privacy scans reject credential-like text and unsanctioned content.
- PNG validation checks signature, chunk tiling, and CRC; MP4 validation checks
  complete box tiling and required media metadata.
- Manifest mutation after approval blocks finalize.
- Capture cannot finalize without an approval bound to the exact bytes.
- Full fake happy path yields exactly seven structurally valid screenshots and
  one structurally valid video.
- All tests prove zero real `adb`, emulator, and `apksigner` contact.

### Preparation lease

- Two cold boots reproduce the exact snapshot and device identities.
- Target-package absence is proven at snapshot creation and after each boot.
- Source and clone return to their recorded initial running/stopped states.
- The fixture receipt schema encodes, decodes, and byte-round-trips.
- Evidence-branch force-push and deletion protection is mechanically verified.

### PR B — qualification

- Fresh complete host gate from tracked-clean exact HEAD.
- Fresh device capture using exact merged orchestrator and APK identities.
- Machine-derived journey, mutation, privacy, media, and restoration counts.
- Byte-bound private visual approval followed by finalize.
- Compact receipt schema validation and replay against the external archive:
  fetch the archived bytes, recompute every digest named by `FinalReceipt`,
  require exact matches, and rerun structural-media and privacy validators
  against those bytes with verdicts identical to the receipt.
- Hosted CI green on the exact receipt head.

## Evidence required from each pull request

Each pull request reports:

- exact base and head SHAs;
- complete raw test log with inner and wrapper statuses;
- mechanically derived test, failure, error, skip, record-variant, and
  adversarial-path counts;
- exact changed-path list and `git diff --check` result;
- production/test line counts against the complexity budget;
- toolchain identities used; and
- a non-author consolidated review comment on the pull request.

PR A is staged in reviewable commits: records/codec first, then command and
remote-result boundaries, then orchestration, then evidence/CLI integration.
The first review contact reports per-module and total production line counts
against the 900-line limit; line counts are not deferred until acceptance.

Acceptance uses one final clean-HEAD rerun as its sole evidence. Earlier runs
are diagnostic and are not combined into a synthetic final receipt.

## Stop and retry law

- A counted failure is a real-fixture authorization that terminates because of
  an instrument defect. The overseer records the count in issue #47. The new
  Python architecture starts at zero and an ordinary correction does not reset
  it; only an owner-approved replacement architecture starts a new count.
- The confirmation-only capability probe is not a capture authorization and
  does not increment the count.
- Two counted failures trigger a mandatory architecture pause. No third
  attempt under that instrument architecture.
- Recurrence of a reviewed defect class escalates immediately to the mechanism;
  no second site-level guard.
- Unparseable or unavailable required status stops before further journey work.
- A fixture identity/pristine-state failure stops before install.
- A restoration verdict other than `verified` stops and reports whether the
  prior state was known, unknown, or known-and-different.
- An unproven runtime cause receives behavior-neutral instrumentation in a
  separate lease. Confirmation and correction never share authority.
- A retry uses a new output directory and fresh authority. Earlier attempts
  remain frozen.

The target metric is one capture authorization consumed per successful capture.
Preflight and device-free dry-run failures consume none.

## Review and communication

Stable requirements live in issue #47, its child issues, and this design.
Agentchattr carries only preflight questions, blockers, compact deltas, and PR
links. Consolidated findings and dispositions live on the pull request.

Every worker lease includes verbatim:

> Preflight: ask your delegator about ambiguity, blockers, scope expansion,
> conflicts, or unverifiable assumptions; else proceed.

Implementers do not grade their own work. The reviewer is selected per PR from
actual authorship and model-family eligibility. Any head change invalidates
prior exact-head approval and evidence affected by that change.

The qualification merge panel deliberately has two exact-head non-author
approvals: one independent reviewer and Sigrid as overseer, with architectural
concurrence folded into the overseer seat under the owner-approved simplified
model. This replaces the former three-seat panel for this sequence.
