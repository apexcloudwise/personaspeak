# Checkpoint: keyboard-fork spike, parked behind UX

**Date:** 2026-07-20
**Status:** Checkpoint — not a design doc. Records an in-progress brainstorm so
the next session doesn't re-derive it.
**Branch:** `docs/fork-spike-and-ux-brainstorm`

## Why this file exists

A brainstorming session opened a question big enough to reopen ADR-0001, then
deliberately parked it to do lower-regret work first. This is the parking
ticket. It records the decisions taken, the evidence gathered, and the exact
shape of the spike so it can be resumed cold.

## How we got here

The session began as "UI/UX prototyping for the persona/mood/AI-config flows."
Three decisions were taken before the direction changed:

1. **Scope** — UX flows first, ahead of the e2e test harness and the setup
   housekeeping. Each gets its own spec → plan → implement cycle.
2. **Surfaces** — panel + onboarding + settings, all three.
3. **Mood model** — mood is an **orthogonal modifier**, not a schema change.
   A fixed app-level list (polite / witty / blunt / apologetic / formal) is
   appended to the persona prompt as an extra instruction. Any persona × any
   mood. `schema_version` stays `1`; `personas/*.yaml` is untouched; the golden
   tests keep passing. Rejected: per-persona moods in YAML, which would have
   triggered the AGENTS.md rule-2 cascade across CLI, skill, Kotlin, validator,
   and fixtures.

Then the switching model itself was questioned, which is where ADR-0001 came
back into play.

## The constraint that drives the whole question

To rewrite a user's message we need two capabilities: **read** the draft and
**replace** it in place. On Android only three mechanisms offer both:

| Mechanism | Verdict |
|---|---|
| IME | Viable — current design |
| `ACTION_PROCESS_TEXT` activity | Viable, but cannot do suggested replies |
| Accessibility service | **Rejected on principle** — reads every field in every app; wrecks the privacy story the README stakes the project on, and AGENTS.md lists privacy-story complexity as a returned-PR offense |

A floating bubble/overlay can display but cannot insert text into another app's
field without that same accessibility permission — it is the accessibility
problem wearing a nicer hat. Also rejected.

## Why the thin IME started to hurt

Two consequences of ADR-0001's "no keys" decision:

1. **The panel cannot be typed into.** While PersonaBoard is the active IME
   there is no other keyboard available, so the `OutlinedTextField` in
   `PersonaPanel.kt` can never receive a character. Found in the previous
   session. The intended fix is to read the draft via `getExtractedText()`
   rather than ask for fresh typing.
2. **The empty state is a dead end.** Switch to PersonaBoard before typing
   anything and there is no draft to read, no keys to type with, and nothing
   to do. This is the first thing a curious new user sees.

These pushed the question: is flip-to-a-keyless-keyboard the right model at all?

## Candidate evidence (gathered 2026-07-20)

| | HeliBoard | AnySoftKeyboard | FlorisBoard |
|---|---|---|---|
| License | **GPL-3.0** | **Apache-2.0** | **Apache-2.0** |
| Language | Kotlin 37% / Java 32% / **C++ 31%** | **Java 74%** / Kotlin 0.9% | Kotlin + C++ NLP core |
| Latest release | **v4.0, 10 Jul 2026** | v1.13-r1, Feb 2026 | still beta, no 1.0 |
| Scale | 2,480 commits, 722 open issues | 8,870 commits | — |
| Prediction | offline dictionaries | **mature**, incl. next-word | **never shipped** |
| Glide typing | needs closed-source blob | built in | in progress |

Readings:

- **FlorisBoard is the trap.** Best stack match on paper (Kotlin, Apache-2.0),
  but word suggestions slipped 0.4 → 0.5 → still unshipped, and the maintainer
  stated publicly (Oct 2023, discussion #2314) that the project "ground to a
  halt" and he cannot run it alone. Forking it buys an *unfinished* keyboard
  plus its maintenance burden.
- **HeliBoard is the best keyboard and the worst license fit.** Healthy and
  current, but GPL-3.0 relicenses the entire app — the README's Apache-2.0 plan
  goes away — and 31% of it is the AOSP native C++ dictionary engine, the part
  you least want to own and must never break.
- **AnySoftKeyboard keeps the license, breaks the stack.** Apache-2.0 and
  genuinely mature prediction, but 74% Java against a Kotlin/Compose project.

Costs that apply to **all three**:

- **The failure mode changes category.** Today a PersonaBoard crash means the
  user shrugs and switches back to Gboard. Post-fork it means *the phone cannot
  type* — ADR-0001's sharpest argument, and still true.
- **AGENTS.md module law breaks.** "`keyboard` → depends on core-*. The IME.
  Keep it thin" cannot survive ~100k inherited lines. That module's contract
  needs rewriting, not amending.
- **`core-personas` and `core-providers` survive untouched.** Pure Kotlin, no
  Android imports, they transplant into any host. This is ADR-0001's escape
  hatch working exactly as designed.

## DECISION (2026-07-20, later the same session): we are forking

The owner has decided: **PersonaBoard becomes a full keyboard fork. The thin
IME is scrapped.** ADR-0001 is superseded, not amended.

This resolves the open question above. The spike below still runs, but its
question changes from *"should we fork?"* to *"which base do we graft onto?"* —
"none of them" is no longer a permitted verdict.

### What the decision kills

- **Keyboard switching.** No flip to us, no flip back, no `⌨ back` button.
- **The empty-draft dead end.** There is always a keyboard, so there is always
  a way to type.
- **The untypeable-panel problem.** We own the keys; the panel can host real
  input if it needs to.
- **`ACTION_PROCESS_TEXT` as a second entry point.** Not needed — we are always
  present.

### What the decision adds

- **The strip becomes permanent furniture.** It sits above the keys at all
  times rather than being a mode you switch into. The compact strip mockup is
  now the only panel design, not one of two candidates.
- **Onboarding becomes a keyboard migration.** "Replace your keyboard" is a far
  bigger ask than "add a second one," and needs different copy, different trust
  framing, and a believable answer to "what about my autocorrect?"
- **Settings grows a second half.** A daily-driver keyboard must expose
  layouts, languages, dictionaries, glide typing, themes, and clipboard —
  alongside the persona/provider settings.
- **The failure mode changes category**, exactly as ADR-0001 warned: a crash
  now means the user cannot type at all. Crash-freedom becomes a release gate.

### Now urgent: the license

The base choice decides the license. HeliBoard is GPL-3.0, which relicenses the
whole app and ends the README's Apache-2.0 plan. AnySoftKeyboard and
FlorisBoard are Apache-2.0. This is now a decision to make deliberately rather
than discover.

### AGENTS.md module law needs rewriting

"`keyboard` → depends on core-*. The IME. Keep it thin" cannot survive. The
`core-personas` / `core-providers` contracts stand; the `keyboard` module's
does not.

## The spike: which base

The base choice is a one-way door and the table above is secondhand. Agreed
approach: **clone, build, and graft all three**, then decide from evidence.

### What the spike must prove

Not "do these keyboards work" — they all do. The real question is **how
invasive the graft is**: can we reach the committed text, add a persona strip
above the keys, replace the field contents, and survive the host's theme
engine? That is what tables cannot answer and what we would live with for years.

### The graft task (identical across all three, for fairness)

1. Clone into `~/workspace/scratch/fork-spike/<name>/` at a **pinned upstream
   commit** (recorded).
2. Build a debug APK on this machine's toolchain. **Record wall-clock build
   time** — a 40-minute NDK build is itself a finding.
3. Vendor or depend on `core-personas` + `core-providers`. This is a real test:
   they are Kotlin/JVM modules and ASK is 74% Java with a different Gradle idiom.
4. Add a **persona strip** above the key area — chips for Jeeves / Humphrey /
   Schultz / Bachchan. Tapping one reads the current field, calls
   `FakeProvider.rewrite()`, and replaces the text.
5. Commit on a branch. Report `git diff --stat` vs upstream, files touched,
   build time, and every point where upstream code had to be **modified**
   rather than added alongside.

**Step 5's last clause is the actual measurement.** Files added are cheap;
upstream lines modified are rent paid forever, because each one is a future
merge conflict. That number decides this.

### The verdict space

Superseded by the fork decision above. The spike now ranks three bases rather
than deciding whether to fork at all. If all three graft badly, the answer is
"pick the least bad and budget for the pain," not "go back to thin."

### Execution split

Per darkmill's ADR-0016 pattern — *the agent that writes the code never grades
its own work*:

- **GLM workers** (`oc-bg -m zai-coding-plan/glm-5.2`, one per candidate, each
  in its own scratch clone) do clone, build, graft, commit, and report
  diffstat + build log.
- **Claude (main session)** takes the emulator serially: install, drive,
  capture screenshots/mp4, and grade.

Workers are **blind to images**, so they must also produce machine-checkable
evidence: the APK installs, `uiautomator dump` XML contains the four chip
nodes, and a scripted before/after shows the field text changed. No worker may
conclude "it works" from a screenshot.

Hard constraint: **one emulator, one active IME.** Builds parallelise;
install-and-drive serialises.

## Why UX still goes first

The **strip and result card are base-agnostic** — they look the same on
HeliBoard, ASK, or FlorisBoard, so that work survives whichever base wins.

It also makes the spike sharper. As specced above the graft plants a
deliberately ugly four-chip row. With the strip already designed, each graft
hosts the **real** UI, and the spike measures the honest question: not "can
this codebase host a row of buttons" but "can this codebase host *our* UI."

**Onboarding is NOT base-agnostic and must be redone.** The screens generated
before the fork decision pitch enabling a second keyboard alongside Gboard.
That flow is now wrong: onboarding becomes a migration to a replacement daily
keyboard. Settings likewise doubles in scope.

## Toolchain verified on this machine (2026-07-20)

- JDK 17 at `/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`
  (pass per-invocation; **never** commit to `android/gradle.properties`)
- `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools`
- NDK 26.0.10792818 and 28.2.13676358; CMake 3.22.1 — the C++-heavy candidates
  will build here
- One AVD: `CityZen_Dev` (android-34)
- 748 GB free
- `oc-bg` at `~/.local/bin/oc-bg`; `zai-coding-plan/glm-5.2` confirmed live

## Resuming

1. Finish the UX design → its own spec → plan → implement.
2. Return here. Run the three grafts with the real strip.
3. Write the ADR that supersedes or reaffirms ADR-0001, with the spike evidence
   attached.
