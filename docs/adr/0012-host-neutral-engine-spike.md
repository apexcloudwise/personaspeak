# ADR-0012: Host-neutral prediction engine as a third Gradle root (spike)

**Status:** Proposed (2026-09-04) — spike landed, cutover explicitly not
decided here. Issue #124 segment 1; segment 2 (the FlorisBoard adapter)
and any ASK-host cutover remain gated on their own evidence.

## Context

ADR-0003 kept AnySoftKeyboard as the fork base because its prediction
engine is mature; ADR-0010 added FlorisBoard as an evaluation second
host, gated (among other things) on the suggestion-engine gap. Issue
#124 asked the middle question: can ASK's engine be extracted into a
host-neutral module now, instead of waiting for FlorisBoard 0.6?

Tree analysis (seraph, verified independently by Sigrid on PR #122)
found three coupling layers: the `dictionaries` and `nextword` packages
(almost pure JVM), the orchestration in `ime/app` (`SuggestImpl`,
`SuggestionsProvider`, `BTreeDictionary` — coupled to ASK's
`RxSharedPrefs`/`Logger` and Android storage), and the key-event feed
layer (`AnySoftKeyboardSuggestions`, the dominant risk, which is host
adapter work by definition).

## Decision

Build the engine as a **spike copy** in a third Gradle root,
`android/engine/`, outside both host roots:

- **Pure JVM, no Android imports** — stricter than the module law asks.
  Hosts consume the artifact via composite build (`includeBuild`);
  a `consumer-proof` scratch root demonstrates the coordinates resolve
  and the stack runs (criterion 1 as a build receipt).
- **Copy, not move.** The ASK host is byte-for-byte unchanged; the
  spike proves portability without betting the v0.1.0 release path on
  it. The real cutover (ASK consuming the artifact, deleting its
  copies) is estimated work, not performed work.
- **Two narrow host interfaces** replace the two coupling points #124
  named: `EngineLog` (TAG + format-string, pluggable sink) and
  `EngineSettings` (the six engine-relevant settings ASK read through
  RxSharedPrefs, now pushed synchronously by the host).
- **Storage is an SPI, not a port**: user/contacts/abbreviations
  dictionaries stay host-side; the engine ships `BTreeDictionary`,
  `InMemoryDictionary`, and the loaders.
- The English wordlist path is the **AOSP combined text** parsed into
  the pure-Kotlin trie. The native v1/v2 binary-trie (`.dict` format,
  `getSuggestionsNative`) is deliberately NOT ported in the spike; it
  is the largest residual item (see the module README inventory).

## Consequences

- The repo now has three Gradle roots. The single-APK gate is scoped
  for two Android roots (ADR-0010 P5); the engine root is JVM-only and
  produces no APKs or application projects, so it stays outside that
  gate's concern. CI runs its tests and consumption proof
  (`engine-spike` job) so the spike cannot silently rot.
- Corrections are **reachability + scoring**: the trie only returns
  words matching typed-or-nearby codes per position (or completions);
  edit distance then ranks reachable near-words as fixes. A host
  adapter must supply nearby codes (ASK: `ProximityKeyDetector`) —
  primary codes alone yield completions with no corrections. This is
  the #1 trap for the segment-2 adapter and is recorded in the module
  README.
- The engine module carries ASK's Apache-2.0 headers and the wordlist
  stays license-gated where it already lives (no duplicate copy of
  licensed data in the module; tests read the vendored file).
- Not decided here: whether ASK ever consumes this artifact. The
  cutover estimate (~1 focused week native-trie decision + 2–3 days
  ASK migration) is in the README; the v0.1.0 release path is
  unaffected either way.
