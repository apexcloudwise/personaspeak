# Milestone 2 — evidence receipt

Every acceptance criterion in issue #47, mapped to the command output, artifact,
commit, and timestamp that satisfies it. Screenshots and the recording support
this receipt; they do not supply its verdicts. Where a verdict could not be
produced mechanically, that is stated as a gap rather than filled with a
picture — see [Declared deviations](#declared-deviations).

## Identity

| Field | Value |
|---|---|
| Branch | `feat/issue-47-atomic-ask-cutover` |
| Device-qualified head | `a3c8919c956005685e29ca826f3685fa2e4a1d36` |
| Merge parents | `fd7a42804ee95d035db2ff74731aaecfb46750c6`, `59abe670f23a9479ffe43ea19308169bf9b2b8a3` |
| Accepted base at start of M2 work | `4450ad1d7aa13e627235f5685f9b19fe087982ef` |
| Canonical APK | `android/keyboard/ime/app/build/outputs/apk/debug/app-debug.apk` |
| Canonical APK SHA-256 | `9b3f0fe1e0b04fd619f0f4a63ca19d67b81736a91aa01a10243463c0c3e54f04` |
| Device | `CityZen_Dev`, serial `emulator-5554` |
| Device build | `google/sdk_gphone64_arm64/emu64a:14/UE1A.230829.050/12077443:userdebug/dev-keys` |
| Android / API | 14 / 34 |
| Screen | 1080x2400 @ 420dpi |
| Toolchain | JDK 21.0.11 (`/opt/homebrew/opt/openjdk@21`), Gradle 9.2.1, AGP 8.13.2 |
| Device phase window (UTC) | 2026-08-05T13:44:42Z – 2026-08-05T13:57:12Z |

The APK installed on the device was pulled back off it and hashed: it matches
the host artifact byte-for-byte. See `package.txt`.

## Host gate

One complete invocation of `bash android/scripts/verify-milestone-2.sh` from
tracked-clean `a3c8919c…`. Raw log: `host/verify-milestone-2.log`. Exit 0,
`PASS: milestone 2 gate`.

Counts derived mechanically from the archived XML, not read off the console
(`host/mechanical-counts.txt`, `host/lint-results-debug.xml`):

| Module | Suites | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|---|
| `core-personas` | 2 | 8 | 0 | 0 | 0 |
| `personaspeak-ui` | 6 | 76 | 0 | 0 | 0 |
| `:ime:app` | 118 | 1276 | 0 | 0 | 16 |
| **Total** | **126** | **1360** | **0** | **0** | **16** |

Lint (`lint-results-debug.xml`): **0 errors**, 471 warnings, 0 information.

`core-providers` reports 0 tests because it contains no test sources. That is a
true zero, not a lost result path, and is recorded as a non-blocking adjacent
finding rather than quietly averaged into the total.

Two clean builds, each yielding exactly one canonical APK at the same hash:
`host/two-clean-builds.txt`.

## Acceptance criteria

| # | Criterion | Verdict | Evidence |
|---|---|---|---|
| 1 | Exact APK installed; package identity, version, IME registration | PASS | `package.txt` — on-device APK hash equals host artifact; `versionName=1.13.1`, `versionCode=1`, `minSdk=26`, `targetSdk=35` |
| 1 | IME enabled and selected | PASS | `ime-list.txt` — `mCurMethodId=biz.pixelperfectstudios.personaspeak/com.menny.android.anysoftkeyboard.SoftKeyboard` |
| 2 | Crash-free ASK startup and ordinary typing in an external host | PASS | `commands.txt` §05 — real ASK key taps produced `hello` in `com.google.android.settings.intelligence`, read back from that app's own view hierarchy |
| 3 | Idle, Loading, Review, Applied states in the dedicated row | PASS (visual) | `screenshots/01-idle-keyboard.png`, `02-loading.png`, `03-review.png`, `05-applied.png` |
| 4 | ASK candidates and keys visible and operable throughout | PASS (visual) | `screenshots/03-review.png` shows the PersonaSpeak row, ASK's suggestion strip (`hello · Jello · jello · yellow · helio`), and all four key rows simultaneously |
| 5 | Fake-provider capture and guarded replacement through the real `InputConnection` | PASS | `commands.txt` §07 — Review displayed with editor unchanged, then exactly one mutation on Apply |
| 6 | Dismiss mutates zero times | PASS | `commands.txt` §06 — editor identical before and after |
| 6 | Apply mutates exactly once, no retry | PASS | `commands.txt` §07 — one change on Apply, none after settle |
| 6 | Stale path does not retry or mutate | PASS | `commands.txt` §10 — editor edited after Review, then Apply: zero mutations, none after settle |
| 7 | Settings launch resolves inside the single package | PASS | `commands.txt` §08 — `topResumedActivity=…biz.pixelperfectstudios.personaspeak/com.anysoftkeyboard.ui.settings.MainSettingsActivity`; exactly 1 PersonaSpeak package installed |
| 9 | No fatal exception, ANR, or process death | PASS | `logcat.txt`, `commands.txt` §09 — FATAL EXCEPTION 0, ANR 0, process death 0, `E/AndroidRuntime` mentioning the package 0 |
| 8 | Restoration of prior IME, on-device state, output path, emulator run state | PASS | see below |

## Restoration

| Item | Prior | After | Result |
|---|---|---|---|
| Default IME | `com.menny.android.anysoftkeyboard/.SoftKeyboard` | same | restored |
| PersonaSpeak APK on device | SHA-256 `a5f74843e96a45400300a9ddfba28e8a4af86d62c6690e25adddf7d5251cf008` | same | restored, hash-verified |
| Enabled IMEs | 7 entries | same 7 entries | unchanged |
| Emulator run state | stopped | stopped | restored |
| Canonical host output path | regenerated by the gate; hash `9b3f0fe1…` | same | tracked-clean tree throughout |

The trap (`commands.txt` §12) was installed before any mutation and is
idempotent on every exit path.

**Restoration did not succeed on the first attempt, and the receipt says so.**
The trap reinstalled the prior APK and reported success, then stopped the
emulator roughly two seconds later. On the next boot the package was absent
while the IME setting had persisted. I did not guess at the cause; I re-ran the
restoration on a booted device, verified the prior APK by hash, and then
deliberately stopped and rebooted the emulator a second time to observe whether
the restored state survived. It did — package present, IME correct, APK hash
identical (`commands.txt` §14, §15). The emulator was then returned to its
recorded initial state, stopped, with no `qemu-system` process remaining.

The observed sequence is consistent with the first stop preceding a flush, but
that mechanism is not proven and is not claimed here. What is proven is the end
state, verified across a full stop/boot cycle.

## Privacy

Synthetic content only: the string `hello` and the fake provider's fixed
response. No credentials, personal text, real prompts, provider results,
clipboard data, or contacts were entered or captured.

Scan before promotion (`commands.txt` §16):

- `logcat.txt`: **0** occurrences of the synthetic string or the candidate text —
  the app logs no content. Retained after filtering out
  `AccessibilityNodeInfoDumper` lines (3537 → 3481).
- Credential-pattern matches in the retained log: **0**. The raw log's 46
  `password` matches were all the accessibility dumper's `password: false`
  boolean attribute, and its 3 `credential` matches were Settings
  preference-controller class names. Both categories are removed by the filter;
  neither was a credential.
- Screenshots and the recording show the synthetic string, the fake candidate,
  and ordinary Settings UI. `01-idle-keyboard.png` also shows two pre-existing
  emulator search-history entries (`High contrast text`, `Hide from pull-down
  shade`) — generic settings names on a development emulator, reviewed and
  approved.
- **Rejected artifacts: 0.** Nothing was captured that had to be withheld.

## Declared deviations

1. **`assertHeightIsAtMost` does not exist.** The immutable corrective plan
   (lines 595–598) specifies it; `androidx.compose.ui:ui-test` 1.8.2 provides
   `assertHeightIsAtLeast`, equality assertions, and `getUnclippedBoundsInRoot`,
   and nothing named `…IsAtMost`. The invariant is unchanged — the Review body
   is asserted `<= 120.dp` — measured directly instead. Overseer-authorised as a
   mechanism-only substitution; the hash-pinned plan was not edited.
2. **`/usr/libexec/java_home -v 21` does not resolve on this host.** The JDKs are
   Homebrew-managed and unregistered with `java_home`. Every gate ran with an
   explicit `JAVA_HOME=/opt/homebrew/opt/openjdk@21`, recorded in the raw logs.
   No claim is made that the plan's literal `java_home` invocation succeeded.
3. **Task 4 qualification gate ran in a detached worktree.** The overseer
   forbade stashing the then-uncommitted Task 5 work, and the gate requires a
   tracked-clean tree; both could not hold in one worktree. The gate ran against
   the exact same commit in a clean detached checkout. Overseer-accepted; only
   the absolute path differs.
4. **Stale build output was removed by hand.** `verify-single-apk.sh` is
   read-only by design — a stale artifact is a finding, never something the
   verifier tidies away — so the dead `android/app` and `android/keyboard-stub`
   build directories and the pre-existing `android/outputs/` copy were removed
   as an operator action, with their hashes recorded first (`commands.txt` and
   the branch history).

## Known evidence gap

**Criteria 3 and 4 are supported by screenshots, not by mechanically derived
IME view bounds.** Issue #47 asks that UI-hierarchy bounds supply the geometry
verdict. On this setup they cannot: `uiautomator dump` traverses only the
focused application window, and the IME is a separate window owned by
`biz.pixelperfectstudios.personaspeak`. The dump of the host app returns 38
nodes, none of them the keyboard's. `dumpsys window` confirms the IME window
exists and gives its outer frame, but not the internal bounds of the row, the
candidate strip, and the key rows.

Obtaining those bounds requires either an instrumentation test setting
`FLAG_RETRIEVE_INTERACTIVE_WINDOWS` or an installed accessibility service. Both
are source additions, which the implementation/device phase boundary forbids
once the device phase has opened.

What does exist for those two criteria:

- the geometry contracts themselves are asserted mechanically **on the host**,
  in `RewritePanelTest` — every control `assertHeightIsAtLeast(48.dp)`, and the
  Review body measured `<= 120.dp` against a 300px pre-expansion sample. That
  assertion was verified load-bearing by mutation: with the cap removed the body
  measures 462dp and the test fails;
- `dumpsys window` confirming the IME window belongs to the single PersonaSpeak
  package;
- screenshots showing the row above ASK's suggestions and keys, with nothing
  obscured.

This is recorded as a gap in the *form* of the evidence for two criteria, not as
a passing verdict dressed up. It is the overseer's call whether the host-side
geometry proof plus visual confirmation is sufficient for M2, or whether a
separately leased instrumentation addition is required.

## Artifacts

| Path | Contents |
|---|---|
| `commands.txt` | complete device-phase transcript with exit statuses |
| `package.txt` / `package-prior.txt` | install identity and hashes, before and after |
| `ime-list.txt` / `ime-list-prior.txt` | IME registration, enablement, selection |
| `logcat.txt` | privacy-filtered log covering the whole device phase |
| `screenshots/` | 7 approved screenshots |
| `journey.mp4` | 40s recording: typing, rewrite, review, apply, return to typing |
| `host/verify-milestone-2.log` | complete raw host-gate log at the qualified head |
| `host/mechanical-counts.txt` | counts derived from the archived XML |
| `host/lint-results-debug.xml` | lint XML the counts were derived from |
| `host/two-clean-builds.txt` | both clean builds and their APK hashes |
| `precutover-commands.txt` | earlier pre-cutover qualification record |
