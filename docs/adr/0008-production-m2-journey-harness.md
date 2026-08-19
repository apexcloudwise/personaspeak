# ADR-0008: Production M2 device journey harness design

**Status:** Accepted (owner decision, 2026-08-10)

## Context

To finalize Milestone 2 qualification without modifying the merged keyboard product, we must implement and wire a production `JourneyHarness` that drives the orchestrator through the real CLI against adversarial fake tools.

This ADR defines the architecture, boundary interfaces, pins, restoration model, and privacy screening constraints for the production harness.

## Decisions

### 1. Ownership and Module Boundary
The production harness will live in exactly one new module: `android/scripts/m2_device/adb_harness.py`. It owns validated capture config, device commands, strict output parsing, selectors, journey execution, and a private command ledger.

We amend the project complexity budget to exactly six production modules and at most 1,800 total nonblank, noncomment lines:
- `adb_harness.py` is capped at 650 lines.
- The remaining five files (`cli.py`, `commands.py`, `orchestrator.py`, `evidence.py`, `records.py`) have a remaining budget of 1,150 lines.
- Static tests enforce these limits.

Amended 2026-08-19 (Stage 5 pre-run probe, issue #55): the instrument pins
were re-derived from the accepted #56 receipt and the live fixture, replacing
fake-echo values the fakes had printed back into the harness. The emulator pin
became 36.6.11 (the prep ran 34.2.16 per the #56 archive, but that version is
no longer installable; the probe proved 36.6.11 loads the pinned snapshot).
The launch argv gained `-gpu swiftshader_indirect`: the snapshot was saved
under the software renderer, and under the 36.x default (gfxstream) the load
is refused and the emulator cold-boots instead, failing the run's
preconditions. The density probe reads `qemu.sf.lcd_density`
(`ro.sf.lcd_density` is empty on this image); `versionName` is 1.13.1 (the
vendored keyboard's numbering); the signer pin is the on-device
PackageSignatures digest; the enabled-IME baseline is the receipt's list.
Review round 2 (PR #76) hardened the signer gate: the dumpsys value is a
32-bit Signature.hashCode kept only as device-side corroboration, while the
gate itself is the signing certificate's SHA-256 pinned from
`apksigner verify --print-certs` and compared as an exact output line
before any install mutation. The budget limits are unchanged.

Amended 2026-08-11 (issue #65 execution-boundary totality) to 750/2,100, amended
2026-08-16 (issue #65 review findings) to 850/2,300, again the same day (issue
#63 fixture transaction) to 1,000/2,500, and once more that day to the round
numbers `adb_harness.py` ≤ 1,100 and total ≤ 2,700 — the fourth raise was taken
at review suggestion because 997/1,000 left no headroom and per-change
amendments cost more than one deliberate round-number raise covering the
remaining #64/#62 stages. The #65 raises cover the provisional-ownership gate,
the ledgered screenrecord boundary, bounded fallback PID termination,
`SignalInterrupt`, and `TerminateOutcome` group-extinction verification; the
#63 raises cover the fixture-byte transaction, pristine/editor pins,
key-geometry validation, stale candidate retention, and private restoration
facts. The budget's purpose — a small, reviewable, stdlib-only instrument —
is unchanged.

### 2. UI-Automation Mechanism
We use subprocess-driven, argv-only commands dispatching to `adb shell input tap`, hierarchy XML dumps parsed with Python's standard library `xml.etree.ElementTree`, package/IME commands, `screencap`, and `screenrecord`.
We reject heavier or less-reproducible alternatives:
- Appium, Maestro, or similar frameworks (adding runtime/node dependencies and complex configuration).
- On-device instrumentation APKs (violates the constraint to not modify or smuggle test helpers into product code).
- Third-party adb python libraries (which hide stderr/stdout separation and remote exit codes).

### 3. Dependencies
No new runtime or external testing dependencies are permitted. We use only installed Android tools and the Python standard library.

### 4. Tool and Status Separation
We execute all commands via subprocess argv lists. Argv, stdout, stderr, timeout, local status, and remote status are treated as separate structured facts.
We do not text-match localized adb errors or append a status sentinel to stdout. Redacted or sensitive command outputs are stored in private digests/run-bound artifacts, keeping only synthetic content and metadata in the ledger.
The ledger artifact (`artifacts/command_ledger.json`) is persisted at the end of cleanup as a private (0600), atomically replaced file. Runs that fail before the emulator launches (preflight, capture-context) produce no ledger artifact by design — nothing device-facing has run yet; the absence is expected, not a lost file.

### 5. Pinned Fixture Invariants
We pin the accepted #56 pristine fixture receipt `dad6f7ac3b3c10ac7b88dfe2397746acb11ee6a42957cf2d1fee7afe1325bdb0`:
- AVD: `M2_Qual_Fixture`
- Snapshot: `m2_pristine`
- System image: `system-images;android-34;google_apis;arm64-v8a`
- API Level: 34
- ABI: `arm64-v8a`
- Fingerprint: `google/sdk_gphone64_arm64/emu64a:14/UE1A.230829.050/12077443:userdebug/dev-keys`
- Locale: `en-US` from `ro.product.locale`
- Screen resolution: 1080x2400 at 420 dpi
- Timezone: `Asia/Kolkata`
- Animations: Window/transition scales set to 1.0, animator scale unset.
- IME Baseline: Gboard LatinIME and Gboard plus Google Voice Input enabled.
- Package Baseline: Zero `biz.pixelperfectstudios.personaspeak` packages.
- Snapshot content file hashes:
  - `ram.bin`: `a46053dddc85a1bfc2be298a955bce07a14fb6dbe183bff6052ee727fcfee6f1`
  - `textures.bin`: `23661254fc0982e69795a9486e8c23bc85802ff57faf118f22d11937f489e68d`
  - `hardware.ini`: `076562d6c8733c97b2818c51c0e571d2052962d8dff30b9905c2ecf4d049a3a3`

### 6. Pinned Editor Contract
We use the disposable Settings search editor:
- Package: `com.android.settings`
- Action: `android.settings.SETTINGS`
- Selector: A unique hierarchy search field element checked to be empty at pre-mutation.
- Source text: `Tea at six.`
- Stale text: `Tea at seven.`
- Synthetic rephrased output from `FakeProvider.rewrite()`: `I have taken the liberty, sir, of rephrasing your words: “Tea at six.” — though I must confess the genuine article is still en route.`

### 7. Restoration and Verification
We capture `PriorDeviceState` after attach but before any mutation. Restoration runs on every post-launch path (including timeouts, exceptions, SIGINT/SIGTERM):
- We restore package, IME, and editor state.
- We check the restored state against `PriorDeviceState`.
- We discard snapshot saves by running the emulator with `-no-snapshot-save` as containment.
- We release the owned emulator via termination and verify release.
- A mismatch in post-restore read is recorded as `RESTORATION_MISMATCH`.

### 8. Privacy Screening and Evidence Binding
Only the `artifacts/` subtree is bound by the manifest. The manifest and `capture-record.json` reside outside the manifest layout.
All raw media, logs, drafts, and responses are kept outside the repository in private directories. Visual media must be approved by the reviewer before finalization. Any digest mismatch or validation failure blocks finalization.

### 9. Separation of Authority with #55
No real device, emulator, or AVD is contacted or modified during the device-free implementation PR. Real-device execution and raw media promotion remain the exclusive authority of issue #55.

## Rejected Alternatives

- **Appium/Maestro**: Rejected due to dependency bloat and lack of isolation.
- **On-device test helper APK**: Rejected to avoid polluting product code with test code.
- **Sentinel-based exit code protocol**: Rejected as brittle and prone to collision with normal output streams.

## Consequences

- We introduce `adb_harness.py` and its tests.
- We update the line count limit to 1800 lines total.
- The repository remains clean of large binaries, preserving device-free verification.
- Issue #55 remains blocked until #59 merges.
