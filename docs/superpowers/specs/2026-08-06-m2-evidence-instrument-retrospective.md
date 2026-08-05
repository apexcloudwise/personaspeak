# M2 evidence-instrument retrospective

**Date:** 2026-08-06

**Incident:** Three authorized qualification captures produced three
instrument failures and zero screenshots. All stopped safely before product
mutation. PersonaSpeak product code was not implicated.

## Executive finding

The safety discipline worked. The evidence instrument did not.

Stop-and-report, restoration attempts, frozen artifacts, behavior-neutral
diagnosis, exact-head verification, and independent review prevented a tooling
failure from becoming a product change or a false acceptance. The cost was too
high: a one-purpose inspector grew to roughly 2,300 lines across shell and
Python and consumed three device authorizations without reaching the keyboard
journey.

The remedy is not weaker evidence. It is a smaller status-safe instrument,
adversarial host testing of the real execution path, and an explicit retry
ceiling.

## What protected correctness

- Every unexpected result stopped rather than being coerced into success.
- Diagnosis and correction never shared a device-run lease.
- Product code was never changed in response to an instrument failure.
- Exact instrument hashes and raw capture artifacts stayed frozen.
- Inner/remote status was never inferred from a successful wrapper status.
- The emulator returned to stopped/no-device state after every attempt.
- Final host acceptance used a single complete clean-HEAD run with mechanical
  XML/lint counts.
- Different-family, non-author review separated sound implementation from
  incomplete evidence.
- Splitting PR #51 allowed proven source/build work to merge while issue #47
  retained every real-device criterion.

These controls remain mandatory.

## What caused churn

### The substrate was wrong

macOS Bash 3.2.57, `set -euo pipefail`, `set -x`, and a command-substituting
`PS4` corrupted status-preserving assignments. The first correction guarded
the site that failed. The next capture reached a second site in the same defect
class. Fourteen sites shared the mechanism.

The lesson is mechanism-first correction. When shared runtime behavior is the
defect class, enumerate the class and replace the substrate; do not award each
call site its own expensive discovery ceremony.

### The real child path was under-tested

The early host harness exercised a stub child and happy helper paths. It did
not drive the real child with legitimate nonzero command results. Its passing
count therefore measured the tested approximation, not the instrument that
would touch the emulator.

The actual orchestrator must run end to end against adversarial fake tools
before device authority exists.

### Remote status was an unframed record

The child appended `__RC=0` to remote stdout. A payload without a trailing
newline fused directly to the marker and made the status undecodable. The
marker was a record type without a framing or round-trip test.

The replacement design carries statuses as structured data and requires every
record variant to encode, decode, and round-trip. It does not append markers to
payload streams.

### Restoration state was captured too late

The third attempt failed before prior package and IME state had been recorded.
Cleanup therefore reported `MISMATCH` against unknown state even though the run
had not installed or selected anything.

The replacement design captures prior state immediately after device attach,
before mutation. Unknown prior state stops with `unavailable`; `MISMATCH` means
known values differ after restoration.

### Environment identity was only half pinned

Instrument files were hash-pinned while JDK and Build Tools executables were
left to `PATH`. `apksigner` and Java 21 were both mechanically derivable from
repository pins but were discovered only at execution time.

Future leases pin tool paths and versions before capture authority is consumed.

### The fixture was not pristine

`CityZen_Dev` retained a PersonaSpeak package from an earlier session. The
instrument verified its own inputs more carefully than the surface it meant to
measure.

Future qualification uses a named snapshot-backed fixture and asserts serial,
fingerprint, API level, screen geometry, and package absence before mutation.

### Evidence lived in the wrong history

The first PR carried a 2.36 MB MP4, seven screenshots, raw logs, and XML. Those
artifacts were provisional, then removed when their receipt was rejected.

Raw evidence and approved media now belong on an append-only evidence branch
under a unique run ID. `main` receives only a compact receipt and immutable
links. Failed and sensitive attempts remain private.

## Failed attempts

| Attempt | Terminal instrument failure | Product/device effect |
|---|---|---|
| 1 | Bash 3.2 tracing corrupted the tracked-clean status assignment | No device contact |
| 2 | Same shared status-corruption class in the emulator probe helper | No device contact |
| 3 | Unframed remote-status marker fused to no-newline payload; prior state had not yet been captured | Emulator booted; no APK/IME mutation; emulator stopped |

## Decisions

1. Retire the shell orchestrator; do not repair or reuse it.
2. Build a standard-library Python orchestrator in small pull requests.
3. Require a real-CLI, adversarial fake-tool dry run before device contact.
4. Capture prior device state first and distinguish unknown from mismatched.
5. Pin repository and executable identities together.
6. Use typed JSON records with an exhaustive round-trip suite.
7. Keep capture and byte-bound visual approval as separate phases.
8. Keep raw evidence out of `main`; link immutable evidence commits and hashes.
9. Pause architecture after two instrument-class failures, not three.
10. Judge success by keyboard evidence produced: target one authorized capture
    for one successful receipt.

## Workflow changes

- Stable requirements move from long Agentchattr leases into issue #47, child
  issues, and the committed design.
- Agentchattr carries preflight questions, blockers, compact deltas, and links.
- Consolidated review findings and dispositions live on the pull request.
- A fix lease enumerates every instance of the defect class and explains why
  the correction covers the mechanism.
- Unproven runtime causes receive behavior-neutral instrumentation first;
  confirmation and correction remain separate.
- Every PR is small enough to reject independently and selects its implementer
  and reviewer from actual authorship.

## Success measure

The previous instrument consumed three capture authorizations and produced no
accepted capture. The replacement succeeds when one authorized capture reaches
the keyboard journey, produces seven validated screenshots and one validated
video, restores the pinned fixture, and yields a compact independently
replayable receipt.
