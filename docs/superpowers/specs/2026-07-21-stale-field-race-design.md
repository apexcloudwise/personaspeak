# Stale-field race — design spec for the transform pipeline guard

**Date:** 2026-07-21
**Status:** Spec — informs issue #7's fork-base ADR; implementation waits on the
base pick. Not an ADR. The binding base+license decision rides with #7; this doc
is the contract that whichever base wins has to honour before a real provider
replaces `FakeProvider`.
**Branch:** `docs/stale-field-race-design`
**Closes:** issue #6
**Related:** [fork-spike results — "Independent review"](2026-07-20-fork-spike-results.md#independent-review-2026-07-21) ·
[keyboard UX design §3 result card](2026-07-20-keyboard-ux-design.md#3-the-result-card) ·
[ADR-0002 pluggable provider registry](../../adr/0002-pluggable-provider-registry.md)

## What this is (and isn't)

This is a **spec for the guard**, not the base pick and not the implementation.
It says what the guard must do, where it sits in the module graph, and what its
contract is — concrete enough that the implementation ticket is a translation
exercise, not a design interview.

It is **deliberately base-independent.** The fork base (HeliBoard /
AnySoftKeyboard / FlorisBoard) is undecided (issue #7). Whichever one wins, the
contract below is the same; only the InputConnection adapter the base supplies
differs. Per AGENTS.md module law, `core-personas` and `core-providers` stay
pure Kotlin — so the guard is expressed in types that carry no Android imports,
and the InputConnection plumbing stays on the keyboard side where it belongs.

It is also **deliberately code-free.** No `keyboard/`, `app/`, or `core-*` files
are touched in this PR, and the persona schema does not move. There are therefore
no goldens or regression tests to ride — called out in the PR checklist, not
slipped past it.

## 1. Problem statement

All three candidate transform pipelines share one unguarded async race. It is a
property of the pipeline shape, not of any one base, which is why it surfaced
identically across HeliBoard, AnySoftKeyboard, and FlorisBoard in the
[independent review](2026-07-20-fork-spike-results.md#independent-review-2026-07-21).

### The race, plainly

The pipeline is three steps:

1. **Capture.** The user taps the transform button. The keyboard reads the draft
   text from the focused field via `InputConnection`. HeliBoard and FlorisBoard
   also hold onto the `InputConnection` reference itself for the replace step.
2. **Await.** The keyboard calls `CompletionProvider.rewrite(system, text)` —
   a `suspend` function (ADR-0002). Control returns to the user while it runs.
3. **Commit.** When the result lands, the keyboard writes it back:
   `selectAll` + `commitText` (HeliBoard, FlorisBoard), or
   `deleteSurroundingText(beforeLength, afterLength)` + `commitText`
   (AnySoftKeyboard), using the **lengths captured at step 1**.

Between step 1 and step 3 there is no re-validation. The commit lands
unconditionally: it does not check that the same field is still focused, that
the field's contents have not changed, or that the connection is still valid.
Three things can go wrong, and the third is worse than the other two:

- **Focus moved.** The result commits into a different field — the search bar
  the user tapped into while waiting, the password field they switched to,
  the next chat thread.
- **Content changed in the same field.** The user kept typing. The rewrite
  clobbers words that did not exist when the request was made.
- **AnySoftKeyboard's variant is sharper.** It re-resolves the connection after
  the await (so it does, in principle, talk to whatever is focused now), but
  replaces via `deleteSurroundingText(beforeLength, afterLength)` using lengths
  captured from the **old** draft. If focus moved, those stale lengths delete
  from a different field. This is why the results doc softened "immune by
  construction" to "not susceptible on the tested path" — the same
  `deleteSurroundingText` mechanism that dodges the composing-state bug is what
  makes this race worse here, not better.

### Why `FakeProvider` hid it

`FakeProvider` waits a fixed 400ms and returns. A user who taps transform and
then does nothing for 400ms looks exactly like a user who *couldn't* do anything
in 400ms. The race was always live in every on-device run of the spike; the fake
just never gave the user enough time to lose.

A real provider is not so considerate. Multi-second, variable latency is the
ordinary case for cloud APIs (ADR-0002's Gemini-free default and the BYOK
cloud options), and the user is free to keep typing, switch fields, or back out
of the keyboard for the entire window. Under a real provider the race is not an
edge case — it is the default shape of every rewrite that takes longer than the
user's patience.

### The UX-rule violation, stated exactly

The [keyboard UX design](2026-07-20-keyboard-ux-design.md#3-the-result-card)
lays down one rule that the rest of the result-card design hangs from:

> The user's own words are never destroyed without a tap.

The transform button is the tap. It happens at **step 1**, before the async
window opens. There is no tap at **step 3**, and no check. So under a real
provider the rule is upheld by coincidence (the user happened not to touch
anything), not by anything the keyboard does. Once the fake is retired, the rule
is upheld not at all.

Restoring the rule does not mean adding a second tap before every commit. It
means the commit at step 3 is **conditional** on the world still matching the
world we captured at step 1 — and if it doesn't, we decline to commit and tell
the user, non-destructively, that we declined. The rest of this spec is that
sentence made into an interface.

## 2. The guard — editor identity + generation token

The guard captures a snapshot of the editor state at request time and re-validates
it when the result lands. If the snapshot is stale, the result is discarded and a
non-destructive "couldn't apply" state is surfaced. The user's draft is never
overwritten by a stale commit.

### What is captured at request time

An immutable **`EditorSnapshot`**, containing four things:

| Field | Meaning | Why |
|---|---|---|
| `fieldId` | Identity of the focused editor. | Detects focus changes between fields. |
| `contentHash` | Hash of the draft text + selection at capture. | Detects in-place edits to the same field. |
| `generation` | Monotonic counter, bumped on every new request and on focus changes. | Lets a newer request *supersede* an older one even if both are still in flight. |
| `selection` | The selection range at capture. | The replace step targets the captured selection; if it has moved, the snapshot is stale even if the text hasn't. |

The snapshot is a pure value — no Android types. That keeps it inside
`core-providers` (or a small adjacent pure module) without breaking the module
law. The keyboard side constructs it from whatever the base's `InputConnection`
exposes; the guard consumes it without knowing where it came from.

### What is re-validated when the result lands

Four predicates, all of which must hold for the commit to proceed. If any fails,
the result is discarded — never committed.

- **P1 — same field focused.** The `fieldId` in the snapshot still matches the
  currently-focused editor.
- **P2 — same generation.** The snapshot's `generation` is still the latest. A
  newer request, or an explicit focus-change bump, makes it stale.
- **P3 — content still matches.** A fresh hash of the field's current text +
  selection equals `contentHash`. Any keystroke invalidates the snapshot by
  default (the tolerance question is open question 2 in §4). Because this
  predicate authorizes a *destructive* commit, the implementation may compare the
  captured draft text directly — it is already in hand as the `text` argument —
  rather than trust a 64-bit hash on the match direction: hash inequality is
  conclusive ("definitely changed → discard"), but hash equality is exactly where
  an exact compare is strictly safer.
- **P4 — connection still valid.** The `InputConnection` the keyboard holds is
  non-null and still accepting text (`isAcceptingText()` true, where the base
  exposes it). Catches dismissal, app backgrounding, configuration changes.

### The discard path

On any predicate failure the guard returns a `Discard` outcome carrying a reason.
The keyboard **does not commit.** It surfaces the result card's Error state per
[keyboard UX design §3](2026-07-20-keyboard-ux-design.md#3-the-result-card):
amber advisory (never red), icon + short bold cause + in-voice body + `Try again`
/ `Dismiss`, and **the draft stays visibly intact.** The error copy names the
cause plainly — focus moved, the text changed, the connection went away — and
states that the user's text was not touched.

The card never destroys anything. The user can `Dismiss` and the field is
exactly as they left it, or `Try again` to re-request against the current state.

### Cancellation semantics

An in-flight rewrite is cancellable, in the structured-concurrency sense: the
guard runs inside a `Job` the keyboard holds, and three events cancel it:

- **New typing in the target field.** A keystroke bumps the generation; the
  in-flight job is cancelled. (Optionally — see the tolerance question in §5.)
- **Focus change.** The field-id or generation bumps; the in-flight job is
  cancelled.
- **A newer transform tap.** Generation bumps; the older job is cancelled, the
  newer one starts against the current snapshot.

Cancellation is a contract obligation on providers, not a courtesy. A
`CompletionProvider.rewrite` implementation whose HTTP client ignores coroutine
cancellation will keep a request alive after the user has moved on — burning
tokens and quota against a result nobody will commit. The provider contract
tests (ADR-0002) should include a cancellation-cooperation case: cancel the
caller, assert the provider's network call is also cancelled within a bounded
window.

Cancellation and discard are complementary, not redundant. Cancellation stops
work the user has signalled they no longer want. Discard catches the case where
the work finished but the world moved on between the last cancellation check and
the result landing — the P1–P4 predicates are the floor that holds even if
cancellation races.

### Interface sketch

Concrete enough to implement against. The types below are pure Kotlin — no
`android.*` imports — so they live cleanly in `core-providers` without bending
the module law.

```kotlin
// core-providers: pure Kotlin. No Android imports.

/** Opaque identity for the focused editor. Built keyboard-side. */
@JvmInline
value class FieldId(val value: String)

/** Stable hash of (text, selection) at capture time. */
@JvmInline
value class ContentHash(val value: Long)

data class SelectionRange(val start: Int, val end: Int)

/**
 * Immutable snapshot of the editor at the moment a rewrite was requested.
 * Captured keyboard-side, validated by the guard. Pure value — no Android types.
 */
data class EditorSnapshot(
    val fieldId: FieldId,
    val contentHash: ContentHash,
    val selection: SelectionRange,
    val generation: Long,
)

/**
 * The keyboard side of the contract. Implemented once per fork base, against
 * whatever InputConnection surface that base exposes. The guard depends only
 * on this interface, never on the implementation.
 */
interface EditorAuthority {
    /** Snapshot of the currently-focused editor, or null if none is focused. */
    fun currentSnapshot(): EditorSnapshot?

    /**
     * Is [snapshot] still the live editor? True iff P1, P3, P4 hold:
     * fieldId matches the focused field, contentHash matches current text,
     * and the connection is accepting text. Evaluated on the IME main thread.
     * P2 (supersession) is checked separately, against [currentGeneration].
     */
    fun isStillValid(snapshot: EditorSnapshot): Boolean

    /**
     * The latest generation. guardedRewrite compares snapshot.generation against
     * this to evaluate P2 (SUPERSEDED) — isStillValid deliberately does not.
     */
    fun currentGeneration(): Long

    /** Bump generation; any in-flight rewrite against an older generation is stale. */
    fun bumpGeneration()
}

enum class DiscardReason {
    FIELD_CHANGED,       // P1 — focus moved
    CONTENT_CHANGED,     // P3 — user typed in the target field
    SUPERSEDED,          // P2 — a newer rewrite was requested
    CONNECTION_INVALID,  // P4 — InputConnection gone / not accepting text
}

sealed interface GuardedRewrite {
    /** Predicates held and [commit] was invoked with [text] inside the validated
     *  turn. Returned so the caller can settle its own UI (dismiss loading). */
    data class Commit(val text: String) : GuardedRewrite

    /** A predicate failed. Nothing was committed; surface the error card. */
    data class Discard(val reason: DiscardReason) : GuardedRewrite
}

/**
 * The guard. Captures a snapshot, awaits the provider, then in a single
 * main-thread turn re-validates (P1–P4) and — only if all hold — invokes
 * [commit] with the rewritten text, returning Commit. Otherwise returns
 * Discard and invokes nothing. The guard never touches the field directly;
 * [commit] is the keyboard's InputConnection write.
 *
 * Crucially, [commit] runs in the SAME synchronous main-thread turn as the
 * final validation, with no suspension point between the check and the write —
 * so no focus-change or keystroke event can be dispatched into the gap
 * (invariant I6). This is why the guard calls [commit] itself rather than
 * returning Commit for the keyboard to act on in some later turn: returning it
 * would reopen the check-then-act window the guard exists to close.
 *
 * The [authority] calls and [commit] execute on the IME main thread; the
 * provider call is awaited off that thread. See invariants I2 and I6.
 */
suspend fun guardedRewrite(
    provider: CompletionProvider,
    system: String,
    text: String,
    authority: EditorAuthority,
    commit: (String) -> Unit,
): GuardedRewrite
```

### Invariants

Load-bearing. Stated plainly, kept that way.

- **I1 — commit is unreachable on a stale snapshot.** The `commit` callback is
  invoked only after a passing re-validation, inside the guard. There is no code
  path that commits on a `Discard`, and no path that skips the guard.
- **I2 — re-validation and commit run on the same thread.** Both the
  `isStillValid` call and the InputConnection commit execute on the IME main
  looper, so the validation cannot be stale-by-thread. (Android marshals
  InputConnection calls on a single thread; the implementation honours that, and
  the test plan asserts it.) Necessary, but not sufficient on its own — see I6.
- **I3 — a discarded result never mutates the field.** `Discard` invokes no
  `commit` and performs no InputConnection writes. The keyboard's reaction is to
  surface the error card, nothing else.
- **I4 — the draft survives every discard.** Whatever the reason, the user's
  field is left exactly as it was at the moment of discard. Backed by I6, this
  holds by construction, not by hope.
- **I5 — generation is monotonic within a keyboard session.** A request is
  superseded iff a strictly-higher generation exists when its result lands. (This
  assumes the session-global scope recommended in open question 5; a per-field
  scope would reset the counter, and this invariant would then hold per field
  rather than per session.)
- **I6 — validation and commit are atomic within one main-thread turn.** The
  final `isStillValid` / `currentGeneration` check and the `commit` write happen
  in a single synchronous main-looper turn, with no suspension point between
  them, so no focus-change or keystroke event can be dispatched into the gap.
  Same thread (I2) prevents stale-by-thread; same *turn* is what closes the
  check-then-act (TOCTOU) window. This is why `guardedRewrite` invokes `commit`
  itself rather than returning `Commit` for the keyboard to act on later.

## 3. Where the seam lives

The pipeline has three layers. The guard is a seam between two of them.

```
+-----------------------------+   capture / commit (InputConnection)
|        keyboard/{base}      |   ---- base-specific, lives in the fork
|   PersonaStrip controller   |
|   EditorAuthority impl      |<--+
+-----------------------------+   |
              |                   |
              | guardedRewrite()  |  pure contract: EditorSnapshot,
              v                   |  EditorAuthority, GuardedRewrite
+-----------------------------+   |
|       core-providers        |   |
|  CompletionProvider.rewrite |   |  suspend, cancellation-cooperative
|  guardedRewrite()  <--------+---+  (the guard itself, pure Kotlin)
+-----------------------------+
              |
              v
+-----------------------------+
|       core-personas         |   persona -> system prompt (untouched)
+-----------------------------+
```

- **Capture and commit are keyboard-side.** They touch `InputConnection`, which
  is an Android type, so they live in `keyboard/` — whichever base #7 picks. The
  base owns the `EditorAuthority` implementation: it knows how to read the
  focused field's identity, hash its text, and check `isAcceptingText()`.
- **The provider call is `core-providers`.** `CompletionProvider.rewrite`
  (ADR-0002). Pure Kotlin + HTTP. Untouched by this design — the guard calls it,
  it does not change.
- **The guard sits at the seam.** `guardedRewrite`, `EditorSnapshot`,
  `EditorAuthority`, and `GuardedRewrite` are pure Kotlin with no Android
  imports. They live in `core-providers` (or a small adjacent pure module if
  that reads cleaner — implementation call).

### Why this is base-independent

The guard depends only on the `EditorAuthority` abstraction, never on a concrete
InputConnection. Each fork base supplies its own `EditorAuthority`
implementation:

- **HeliBoard / FlorisBoard** — already hold the `InputConnection` they captured
  at request time; their `isStillValid` checks the held connection is still the
  focused one and still matches the hash.
- **AnySoftKeyboard** — re-resolves the connection per call (its existing
  pattern); its `isStillValid` checks the *currently-focused* field matches the
  snapshot, which is exactly the check its current pipeline skips. With this
  guard in place, the `deleteSurroundingText` stale-length failure becomes
  unreachable: if focus moved, `isStillValid` returns false and the discard path
  runs before `deleteSurroundingText` is ever called.

The contract is identical across bases; only the adapter differs. Whichever base
#7 picks inherits the same obligation: implement `EditorAuthority`, route every
transform through `guardedRewrite`, and the race closes.

### What this does not change

- **`core-personas`** is untouched. Persona → system-prompt is none of the
  guard's business.
- **`CompletionProvider`** is untouched. The interface in ADR-0002 stays as-is;
  providers gain a cancellation-cooperation obligation, surfaced in their
  contract tests, not in the interface signature.
- **The persona schema** does not move. `schema_version` stays `1`; golden tests
  unaffected.

## 4. Open questions / deferred to implementation

These are real decisions, not rhetorical ones. They are listed here so the
implementation ticket has to answer them, not so this spec can pretend they're
settled.

1. **Field identity mechanism.** `InputConnection` instance equality?
   `EditorInfo.fieldId`? A composite (instance + package of the bound app)?
   Android does not hand you a hard, stable field identity across reconnections
   (rotation, process death), so the honest options are a *soft* identity
   (instance + content hash, accepting that reconnection invalidates) or a
   best-effort *hard* identity that degrades to soft. The spec requires *an*
   identity; it does not prescribe the mechanism.
2. **Content-difference tolerance.** Exact match — any keystroke invalidates —
   is simplest and safest. Trailing-typing tolerance (accept appends, reject
   edits within the captured region) is friendlier but adds failure modes. Start
   strict; loosen only with a stated UX reason.
3. **Retry UX on discard.** The error card offers `Try again`. Does retry
   re-request against the *current* field state (the user may have edited it),
   or against the *captured* state? Current state is the only honest answer —
   the captured state may no longer exist — but the card copy should say so.
4. **Cancel vs. let-finish-and-discard.** Cancellation saves provider tokens and
   quota; discard is safer (no race between cancel and commit). Default: cancel
   on focus change and on supersession; let-finish-and-discard only where the
   provider is known not to honour cancellation promptly (e.g., on-device).
5. **Generation counter scope.** Per-field (resets on focus change) or
   session-global (monotone across the whole IME session)? Session-global is
   simpler to reason about and costs nothing; per-field is marginally more
   precise. Pick session-global unless someone finds a reason not to.
6. **`EditorAuthority` location.** In `core-providers` (where the guard lives,
   one module), or in a new tiny pure module to keep `core-providers` focused on
   HTTP? Decide at implementation; the module law is satisfied either way.
7. **Selection-only changes.** If the user moves the cursor without editing the
   text, P3 (content hash includes selection) fails. Is that desired (safer) or
   noisy (the rewrite would still have been fine)? Lean desired, but it's a UX
   call.

## 5. Test plan

The fork-spike results doc names the missing test directly:

> whatever base is chosen, an on-device instrumentation test of the actual
> capture → transform → replace path belongs in the project's test suite, not
> just unit tests against the pure logic layer.

That test is the verification of this spec. It must run against a **real
InputConnection on a device or emulator** — the lesson from the spike is that
every worker's pure-logic tests passed and every on-device run found a bug the
tests couldn't see. The pure `guardedRewrite` logic gets its own unit tests
against a fake `EditorAuthority`; the race scenarios below get an instrumentation
test against the real seam.

The instrumentation test uses a `FakeProvider` with a **configurable, multi-second
delay** (not the fixed 400ms that hid the race) so the mid-flight events land
inside the window. Scenarios:

1. **Happy path.** Tap transform, no input during the wait, result lands, same
   field focused, content unchanged → `Commit`, draft replaced cleanly.
2. **Focus change mid-flight.** Tap transform, switch to a second field during
   the wait, result lands → `Discard(FIELD_CHANGED)`, error card shown, **the
   second field is untouched** (this is the regression test for ASK's
   `deleteSurroundingText` stale-length variant — assert no characters are
   deleted from the newly-focused field).
3. **Typing mid-flight.** Tap transform, type additional characters in the same
   field during the wait, result lands → `Discard(CONTENT_CHANGED)`, error card
   shown, the user's additional typing is intact.
4. **Supersession.** Tap transform, tap transform again before the first lands
   → first request is cancelled (or its result discarded as `SUPERSEDED`),
   second request proceeds against the current snapshot, only one commit
   happens.
5. **Connection invalidated.** Tap transform, dismiss the keyboard / back out /
   background the app during the wait, result lands → `Discard(CONNECTION_INVALID)`,
   no crash, no commit to a dead connection.
6. **Cancellation cooperativeness (provider contract test).** Cancel the
   `guardedRewrite` coroutine mid-flight; assert the provider's underlying
   network call is cancelled within a bounded window. This is a unit test
   against each `CompletionProvider` implementation, per ADR-0002's contract-
   test pattern.

Every scenario states its assertion on the **field contents after the dust
settles**, not just on the returned `GuardedRewrite` variant — because the bug
class is "the wrong bytes land in the wrong field," and the variant alone
doesn't prove the bytes stayed where they belonged.

## What this spec does not decide

- The fork base. That is issue #7's ADR.
- The license. Also #7's ADR.
- The `EditorAuthority` implementation per base. That is the implementation
  ticket, one per base, after #7 lands.
- Anything about suggested replies, on-device providers, or Phase 2. Out of
  scope here; the guard applies to them when they exist, by the same contract.
