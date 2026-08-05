# M2 device-qualification orchestrator design

**Date:** 2026-08-06

**Status:** Draft for owner review

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
- No device mutation during the first two implementation pull requests.
- No claim that timestamped raw evidence is byte-identical across runs.

## Delivery shape: three small pull requests

Issue #47 remains the parent milestone. Each child issue owns one concern, one
author, one non-author reviewer, its own tests, and its own acceptance evidence.
Roles are selected per pull request from actual authorship; they are not
permanent offices with nameplates.

### PR A — typed orchestration core

Build the typed record model, deterministic JSON codec, local process runner,
remote-result interface, toolchain identity model, and restoration state
machine. Tests use adversarial fake processes. No `adb`, emulator, or device
contact is permitted.

The owner-selected roles for this slice are Seraph as implementer and Cassie as
independent reviewer.

### PR B — device-free capture and finalize machinery

Connect the real orchestrator to fake `adb`, `emulator`, and Android tooling.
Implement the complete capture-to-private-archive flow, privacy scan, media
validation, byte-bound visual approval, finalize flow, and compact receipt.
Exercise the actual CLI end to end. No APK install, IME change, product journey,
or other device mutation is permitted. A separately authorized,
confirmation-only capability probe may attach to the disposable fixture to
establish remote-status semantics before the adapter is selected.

### PR C — real-device qualification and compact receipt

Run the already-merged orchestrator against the exact APK from merged `main`.
This PR contains only the compact receipt/index and truthful patch note. Raw
logs and approved media live on the append-only evidence branch. If the real
run discovers an instrument defect, qualification stops; the correction lands
in a separate implementation PR before a fresh attempt.

PR C closes #47 only after the final clean-HEAD host rerun, hosted CI, device
receipt, restoration proof, visual approval, and non-author review all pass on
the exact candidate head.

## Production structure and complexity budget

The implementation lives under `android/scripts/m2_device/`:

| File | One responsibility |
|---|---|
| `records.py` | Immutable result/state types and deterministic JSON codec |
| `commands.py` | Local process execution and the remote-result interface |
| `orchestrator.py` | Phase ordering, stop causes, prior-state capture, restoration |
| `evidence.py` | Digests, privacy checks, media validation, manifests, finalize |
| `cli.py` | Argument parsing and `capture` / `finalize` entry points |

Tests mirror those modules under `android/scripts/tests/m2_device/`. The
production package is limited to these five modules and 900 nonblank,
non-comment lines in total. Crossing either limit is an architecture stop that
requires an owner-reviewed design amendment. Tests and fixtures do not count
toward the line budget; the budget prevents the inspector from becoming the
largest exhibit in its own case.

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
That lease may attach to the disposable fixture and observe a harmless command
only; it may not install the APK, change IMEs, run the product journey, or
select a correction. Its accepted evidence becomes an input to the later PR B
adapter lease. Confirmation and mechanism selection do not share a lease.

If the installed tooling cannot provide a trustworthy remote status, PR B must
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
`CityZen_Dev`. The exact AVD name and snapshot digest are recorded in the child
issue after the snapshot is created through a separately reviewed,
non-destructive preparation lease.

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

`main` receives only:

- `docs/evidence/milestone-2/README.md` — compact criterion-to-receipt index;
- `docs/evidence/milestone-2/receipt.json` — `FinalReceipt`; and
- a truthful `PATCHNOTES.md` entry.

No MP4, screenshot, raw log, XML archive, APK, or rejected artifact enters
`main`. Sensitive or rejected files remain private; only their mechanical
count is recorded.

## Required tests

### PR A

- Every dispatch-table record type encodes, decodes, and byte-round-trips.
- Unknown/malformed record variants fail closed.
- Command results preserve empty output, no-final-newline output, arbitrary
  punctuation, nonzero statuses, timeouts, and signals.
- Remote status `unavailable` cannot be treated as zero.
- Orchestrator state transitions reject mutation before prior-state capture.
- Every partial phase invokes restoration exactly once and records the exact
  terminal cause.
- Tool identities reject missing, conflicting, or drifted versions.

### PR B

- The real CLI runs end to end against adversarial fake executables.
- Every guarded result variant is exercised with a genuinely nonzero command,
  tracked by a coverage matrix rather than a pass-count anecdote.
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

### PR C

- Fresh complete host gate from tracked-clean exact HEAD.
- Fresh device capture using exact merged orchestrator and APK identities.
- Machine-derived journey, mutation, privacy, media, and restoration counts.
- Byte-bound private visual approval followed by finalize.
- Compact receipt schema validation and replay against the external archive.
- Hosted CI green on the exact receipt head.

## Evidence required from each pull request

Every implementation PR reports:

- exact base and head SHAs;
- complete raw test log with inner and wrapper statuses;
- mechanically derived test, failure, error, skip, record-variant, and
  adversarial-path counts;
- exact changed-path list and `git diff --check` result;
- production/test line counts against the complexity budget;
- toolchain identities used; and
- a non-author consolidated review comment on the pull request.

Acceptance uses one final clean-HEAD rerun as its sole evidence. Earlier runs
are diagnostic and are not combined into a synthetic final receipt.

## Stop and retry law

- Two consecutive failures inside the instrument trigger a mandatory
  architecture pause. No third correction lease.
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
