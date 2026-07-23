# Dedicated PersonaSpeak row design

**Date:** 2026-07-24

**Status:** Owner-approved design; written-spec review pending

**Decision:** Mount PersonaSpeak in its own row above AnySoftKeyboard's
candidate row. ASK suggestions and key rows remain visible, usable, and
uncovered in every PersonaSpeak state.

**Authority:** [ADR-0007](../../adr/0007-dedicated-personaspeak-row.md) ·
[Stitch screen contract](2026-07-22-stitch-screen-contract.md) ·
[issue #47](https://github.com/apexcloudwise/personaspeak/issues/47)

## Why this amendment exists

The accepted screen contract already says to mount one permanent PersonaSpeak
row above ASK's inherited suggestion row. Task 5 of the atomic-cutover plan
instead used ASK's `addStripAction` extension point. That API lays an action
over the candidate row and budgets only the candidate-strip height.

The implementation rendered. The first device pass simply looked in the wrong
place, which is an impressively efficient way to discover a layout bug and a
visual-inspection bug at once. Behavior-neutral probes then established the
actual geometry at accepted head
`e1861837763961515026c7b7c83662c35cf5b8b5`:

- the `ComposeView` measured and laid out at 1080×148 px;
- ASK's candidate view measured 1080×95 px;
- the PersonaSpeak view overlapped the candidate view and extended 53 px into
  the keyboard region;
- in-memory, content-free pixel analysis confirmed that the Rewrite and
  Settings controls render at their measured left-side coordinates.

This is therefore not a rendering fix. It is an architecture correction:
PersonaSpeak needs a separately measured row, not a larger strip action.

## Goals

- Preserve ASK suggestions in every PersonaSpeak state.
- Preserve all ASK key rows, touch regions, and typing behavior.
- Keep review-before-replace readable, with full-size actions and no overlay
  over suggestions or keys.
- Keep all PersonaSpeak UI inside the IME input-view hierarchy. No overlay
  permission, dialog window, focus-taking popup, or second keyboard.
- Retain the existing lifecycle, saved-state, `ViewModelStore`, editor-safety,
  and request-scoped privacy boundaries.
- Pay upstream rent through one narrow, generic container seam and record it
  in `android/keyboard/UPSTREAM-MODIFIED.md`.

## Non-goals

- No persona or mood picker implementation in this Milestone 2 correction.
- No provider configuration, persistence, history, analytics, or network
  provider work.
- No changes to the persona schema, pure core modules, or `EditorPort`
  semantics.
- No Task 8 rollback deletion until the revised Task 7 gate passes.
- No attempt to preserve the current full-width-over-strip geometry.

## Chosen layout

The portrait stack, from top to bottom, is:

1. PersonaSpeak row;
2. ASK candidate/suggestion row;
3. ASK key rows.

The PersonaSpeak row is a normal measured child, not a strip action and not an
overlay. Its resting/loading surface has a 48dp minimum interactive height.
Expanded result and error content grows the IME upward while the candidate and
key rows remain anchored below it. Long result text follows the existing
contract: its scrollable body is capped at
`min(320dp, 40% of the IME window height sampled before expansion)`, while its
header and actions remain fixed.

The Stitch mockups are directional layout references, not measurement
authority. Their 36–40dp resting-row estimate yields to the committed 48dp
minimum touch-target rule.

The row is independent of candidate visibility. If ASK has no suggestions to
show, PersonaSpeak does not disappear with the candidate view. Conversely,
showing or hiding PersonaSpeak does not change ASK's suggestion data or
visibility policy.

Landscape keeps the same three regions. It uses compact labels, the same 48dp
touch targets, pinned result actions, and the existing sampled-height cap.
Fullscreen extract mode follows the accepted screen contract: rewriting is
disabled unless `EditorPort` can preserve the normal capture and replacement
semantics, while ASK editing continues.

## State flow

`Idle` shows Rewrite and Settings in the dedicated row. `Loading` replaces the
Rewrite affordance with progress without moving into the candidate row.
`Review` expands the dedicated row upward and shows the candidate plus
`Use this` and `Dismiss`; the host editor remains unchanged. `Message` uses the
same row for typed, sanitized failure copy. Applied, stale, rejected, and
unconfirmed outcomes retain their existing state-machine semantics.

The data path does not change:

1. PersonaSpeak captures through `EditorPort`.
2. The request-scoped coordinator produces a candidate in memory.
3. The dedicated row renders that candidate for review.
4. Only `Use this` authorizes a guarded replacement attempt.
5. Dismissal, failure, or a stale editor produces no mutation.

No draft, prompt, candidate, provider response, or replacement text enters
saved state, logs, screenshots used as raw evidence, or durable history.

## Host architecture

`KeyboardViewContainerView` gains one generic extension-row seam. The seam
inflates a parentless view, owns add/remove lifecycle callbacks, and lays the
view out as a full-width sequential child above `CandidateView`. Existing
`StripActionProvider` behavior stays unchanged for ASK's real candidate-row
actions.

The container measurement contract becomes:

- measure the extension row at the available width and its content height;
- reserve the candidate-strip height exactly as ASK does today;
- add the measured extension-row height to the input-view total;
- lay out extension row, candidate row, and keyboard without intersecting
  rectangles;
- keep existing candidate-row actions over the candidate row only.

PersonaSpeak's host adapter returns a parentless full-width `ComposeView`
through that new seam. It reuses the existing `ImeViewTreeOwners`,
`DisposeOnViewTreeLifecycleDestroyed`, composition setup, finish, and destroy
rules. Compose state changes may request a new measurement; they may not use
translation, elevation, or z-order to borrow space from suggestions or keys.

This requires a deliberate edit to the vendored
`KeyboardViewContainerView.java`. The same commit must update the upstream-rent
ledger with the reason, behavioral delta, and replay guidance. The seam remains
generic; upstream ASK code does not import a PersonaSpeak type.

## Failure and recovery

- If the extension row cannot be measured without overlap, fail the host test
  and stop. Do not fall back to `addStripAction`.
- If available height is too small, keep ASK typing usable and present the
  existing typed unavailable/error state. Do not cover keys or candidates.
- Removal, input finish, and service destruction dispose the composition and
  owners idempotently.
- Configuration, theme, font-scale, orientation, and input-view recreation
  rebuild the row from non-content state only.
- A failed Task 7 device gate leaves both rollback modules intact and blocks
  Tasks 8–10.

## Verification design

The implementation plan must add tests before the correction:

### Container and host tests

- the extension row is parentless before ASK receives it;
- extension, candidate, and keyboard rectangles do not overlap;
- the extension row consumes full available width;
- resting, loading, review, and message heights are included in total
  measurement;
- candidate visibility changes do not remove the extension row;
- existing strip actions still occupy only the candidate row;
- repeated input starts do not duplicate rows or owners;
- finish/remove/destroy are idempotent and clear the composition/store;
- no upstream package imports a PersonaSpeak implementation type.

### Compose and accessibility tests

- all four proof-surface states render inside the dedicated row;
- candidate text scrolls within the accepted cap;
- Rewrite, Settings, Use this, and Dismiss expose at least 48dp touch targets;
- semantics and existing test tags remain available;
- light/dark, small portrait at 200% font scale, and landscape captures have no
  clipping or overlap;
- accessibility announcements contain no editor or provider content unless
  the candidate body receives focus.

### Device gate

The final clean-HEAD Task 7 rerun must preserve complete raw logs and derive
counts mechanically. On the accepted APK it must prove:

- real ASK suggestions are visible alongside the PersonaSpeak row;
- all ASK key rows remain visible and type into an external host;
- Idle, Loading, Review, Message, Apply, Dismiss, and Settings are reachable;
- Review appears before replacement and does not cover suggestions or keys;
- exactly one Apply mutates once; stale refusal mutates zero times;
- no fatal package crash, ANR, or process death;
- the prior IME and accepted APK/state are restored on device and at the
  canonical output path on every exit.

Task 8 remains blocked until a different-model-family non-author accepts this
gate on the final clean head.

## Alternatives considered

### A1 — Capped in-strip review

The candidate and actions fit into the existing 36–40dp strip. Long text
truncates almost immediately, touch targets fight for room, and suggestion
content disappears during review. It preserves the strip's height while
misplacing most of the product.

### A2 — Floating review over keys

Suggestions stay visible, but the review card covers the top key rows. This
breaks the keyboard-intact invariant and turns Review into a modal typing
interruption.

### A-expand — Temporarily enlarge the strip

Idle uses no extra height, but Review grows over the suggestion region. This
recreates the exact candidate-overlap class confirmed on the emulator and
requires weakening the suggestion-preservation contract.

### Replace suggestions with PersonaSpeak

This was withdrawn. ASK's mature suggestion engine is a primary reason
[ADR-0003](../../adr/0003-fork-anysoftkeyboard-apache.md) chose ASK over
FlorisBoard's stubbed engine. Removing those suggestions to make room would be
an unusually thorough way to lose the feature we selected the base for.

## Mockup record

All mockups use synthetic placeholder text and live in Google Stitch project
`377671983474417230`.

### Chosen C flow

- [Idle](https://lh3.googleusercontent.com/aida/AP1WRLsoCxXoIgUyDtnhW_uXPsyTpcF47IvE0txSdKdaGDzUO_kFjUQPNIzc4fijeIAHYT7Pi_Hg8xarVRQFQdLBWtqQOLSQZ2NhelY1ZRMuDlkrSFS3UjNBhfMV6RkBOcAXgg3NsDRiUEX8ds_tMDZHvXmyA66mwCiMYIMxRcjM5zI2XjbrzKcILuLk0qnIbHQiC4dxnkENW0s2ncrHEpqqrcPCCXWTD9D5rAFPnHGkzGFQPvkx7iaW4pzb7w)
- [Loading](https://lh3.googleusercontent.com/aida/AP1WRLu7hNakvrMCF8XJ4KGFK1340x6DirFvNt8lme2-pcnmx__HIM44S2LVPs9wXRBCBj6AZwMhUY80xaUhsYCFSGa8O9UscNV5UCwRoB1O4dJqa7YfzaspTXGiI4ANeb5PdXe-Lm6zTuG1_7Qv6aI1uIxNUjIY2GnEpmsv0ibl87dbvDu9HIGTiUhGYbOR-sy6ZUMD0U33p-WL_zoW-vADCRk8ZnyF-40zjtQ28JsD0JyYi2WHOwMjXGPF)
- [Review](https://lh3.googleusercontent.com/aida/AP1WRLt9pnWbFZCMNEgFlxSDBEyOc3GjT-7gAIB5moeWMlwYJCNpWs7NNpQalTOF_587CfmrLQMRpYCQUvAOVghf0FKYfsHEkRKJnwwKYeccaGWXM4cE7MSjTUrMjdO0a7hZ7KgbcBuTpnnpC3O6sbP7KlwJgWXix5dtuIAZDVrSNCpXGGykV_-i9Paf6wnhuJkQBeGMlYkAarB8fTvk0sz2VGhTz-aVFNuigFZuzkhml0kBUSbNWOev5Ki4eQ)
- [Applied](https://lh3.googleusercontent.com/aida/AP1WRLte6ggeqDpSYOmTvBPhf-BqEHeiwJ_koJU-LG2tW5f5mjYI5wB6Fo95i4GuZD5T94aXboMjNNf9yu9od67x5F-hi51bVy3ICHteiZ5upO-AywB9ogQBEq2toRBvHjuSEg6XHUwUJKf04-5vox6rdrYfcPOuWmQWF8ZQdSKv1SB6BknkufZJWEWCQZrTqUshlVdBEAeB8iHDNkKLBVM-TwTv8oQSiRlo-yZdnKOtNVHkVrS4l_v3dq9dXA)

### Rejected A flow and strict-A studies

- A-expand: [Idle](https://lh3.googleusercontent.com/aida/AP1WRLtsf0qRGeGq09-fJiec5KRpF4Jn4lgDJlCLS1QmlxQUTK6Kof-d1T0g8D_cb5sg85ypu7M1s6N9AJ_whMsCWGvj0N_aZgWrEriF_W2o-Et3iNki8hKI4qELRqremRKE828h5yOfKRptv3Ejfk1_aUdjdgw15JoFM-idtMM4ZYjJfzRicBFtS0hEc2bCm0PZDJJqhg7oaY8S01dzs3sOQZY8zb7asia8QGYHkhKpkhcETnb01-nVeoI2Mg),
  [Loading](https://lh3.googleusercontent.com/aida/AP1WRLtBcbsh6IHEV8rvVX3Zn7GbPY3AGxa2Y6rqx6Mc4Ajj50RK7EZiuSHkQ5tCfler6nihA2PMqsRbnKi8wC8A2NPD0Ff52vGWp4-Xnbf6Kculru7vv5pl55ZsJ1ML75VkjxK3fXs4mWSxQmm_uRgXPrtIgV3g54LmtTsN2V3IyXdKmMUxG13g-iy3EQbycFWAaOXyPE9909R8SME6KMLAGfTCO8dzxpkI54gtEro66Jp5Af9dL4YmoYIq),
  [Review](https://lh3.googleusercontent.com/aida/AP1WRLurKNSphHBVIz3Q3ljIncs_g-W2eUAc32o2XDtQzLh8f1_AXEaHuo1k2jcDXEKr_1MZyNFJjMRgiTwcOgzhtLK4at19pjcpONbAG7frPWL4M4TbqbkauIVzqWjvAu1NPdUwZjaWKlv-USAW3jk32xIV8dk5CebSeTAbCfhJTnjIT5BESngPkLQiEKU7lwIGA2b8-TyNkGP_VPWEgj_xI_rsSJTm2I8kvood56FgS1-SmMwIsoDsk9hLgQ),
  and [Applied](https://lh3.googleusercontent.com/aida/AP1WRLsWP_FmfKQJh5UecWOyjm1FL-MSqrWj3fMAsmlgnhf6UTCcZiflK8Vdud3JBcEYoU1H0NoqwY3Vc-lDCrFXhYBj14qE0aasIUFrOGtKBDo3U0Z_Q39TC4AThCw4QggVGBECdhX_JyJgiDHVYXFp44EmrpNOMc5Hp4sDDXfS7pdVlmAW8GIGFO184GjEX0IwZPIb3XWhfyS0B7OKLA1egTFusshgKCrNvArX9LzviRQaLZbX_vITzWtm)
- Strict-A [shared Idle](https://lh3.googleusercontent.com/aida/AP1WRLsbfxtIFUJHnauc0U8gbi2JqhTZD5kiN6ex1HAlYXJkxo9BV0OUO0BWmk7CxqyfpjwxY-EbZkut1CmjXPwvoXJORQag5utnG1A-Y-NBiBy5u4SR0owuAQAGG2aOArzpO--O41AQ0oQ9o08XmyMQq0Mrjfb6TTSxP7opKGyWu3DAFqNLW6tCJxMwEagIg40-NkTO7T5hgayd_eJCPjD7CQIwG39wcrMKkORtChKctGpSZLyraTAtH-OeqA),
  [A1 in-strip Review](https://lh3.googleusercontent.com/aida/AP1WRLuqCvLgtKT6HdsCH2tqF25eQ6WSQXSsmWhWQGBxjCMlYtZyafh-_AQyV9ywT45MoDJSXpPTPoZwHaUpd1knTie9fnThxT8Kef1hhGI5GPBsF3wQnVprXh00i1ul8iq858c_UHOzHMMbjOjxWbZqNybePeDdrCd1bWki3LvGCo_K4PSCZ-mk2pOFh9YesFLUa1d2icXSCvt7YKLFXdVGvnq7yJMRAQhut__nRalCE2KdnYOa-N4CMPcl7Q),
  and [A2 floating Review](https://lh3.googleusercontent.com/aida/AP1WRLuiz_PQg5flHJ1wFI848zjwHDIdeyUaJZqa-xi5V9nFBih-2JXtgKSgVgEjXI4XdYcJ3IInQ_LMW3VLuLSJXzDPI-RnEE1PPiMnyOc8kql-OpqArzWK44Z9m6gj7VvtgKs4ulRWQl66C0IHIZop9g2NY9X-GzV5l-5slygS1WxqGBDGjxGBT314imABXiDfwT6O-J0D5n1fPwnjy4epJxa7zJRlcXzE1nSS6EQ7qQDcGIYkiuUYcGhg7Q)

## Plan impact

Tasks 1–6 remain historical accepted work at `e186183`. Task 7 is paused
before device qualification. After this written spec is reviewed, a revised
implementation plan must replace the Task 5/6 strip-action attachment with the
dedicated-row seam, add the tests above, and define a new clean-head Task 7
gate. Tasks 8–10 remain blocked until that gate passes.
