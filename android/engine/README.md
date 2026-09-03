# personaspeak-engine — host-neutral prediction engine spike

Issue #124, segment 1 (ADR-0010 / PR-stack P7). A **spike**: the engine
sources are **copied** from the vendored AnySoftKeyboard tree (Apache-2.0,
headers preserved), not moved — the ASK host is byte-for-byte unchanged,
and the real cutover (ASK consuming this artifact) is estimated below,
not performed.

## Layout

- Own Gradle root (`android/engine/`), outside both host roots (the ASK
  unified root at `android/`, the FlorisBoard root at `android/florisboard/`).
- `:engine` — pure-JVM Kotlin. No `android.*` imports, no Rx, no
  host classes. Hosts consume it via composite build:
  `includeBuild("../engine")` + dependency on
  `com.personaspeak.engine:engine`.

## Ported layers (source → engine)

| ASK source (com.anysoftkeyboard.*) | Engine (com.personaspeak.engine.*) | Coupling swap |
| :-- | :-- | :-- |
| `dictionaries.Dictionary`, `EditableDictionary`, `WordComposer`, `WordsSplitter`, `KeyCodesProvider`, `GetWordsCallback`, `InMemoryDictionary` | `dictionaries.*` | `Logger` → `EngineLog`; `TextUtils` → plain checks; `KeyCodes` constants inlined |
| `nextword.*` (storage, parsers, container, dictionary) | `nextword.*` | `Context` → base-`File` injection; `android.util.Log` → `EngineLog`; `ArrayMap` → `HashMap`; prefs-backup provider dropped |
| `ime/app` `BTreeDictionary` | `dictionaries.BTreeDictionary` | word-cap resource → constructor parameter |
| `ime/app` `Suggest`/`SuggestImpl`/`SuggestionsProvider` | `suggest.*` | `RxSharedPrefs` → `EngineSettings` interface; `Logger` → `EngineLog`; contacts/sqlite/quick-text addons excluded (host storage SPI, see inventory) |

## The two narrow host interfaces (#124's requirement)

- `EngineLog` — TAG-scoped d/i/w/e; no-op default, host plugs its own sink.
- `EngineSettings` — the six engine-relevant settings ASK read through
  RxSharedPrefs (`quick_fix`, `quick_fix_second_disabled`,
  `use_contacts_dictionary`, `use_user_dictionary`,
  `next_word_suggestion_aggressiveness`, `next_word_dictionary_type`),
  as synchronous properties; the host pushes updates.

## English dictionary (criterion 2)

The AOSP LatinIME combined wordlist
(`android/keyboard/addons/languages/english/pack/dictionary/en_wordlist.combined.gz`,
Apache-2.0, vendored and license-gated by `verify-dictionary-licenses.sh`)
is parsed by `dictionaries.CombinedWordListLoader` from a plain
`InputStream` into an `InMemoryDictionary` (BTreeDictionary subclass).
Tests read the vendored file by relative path — no duplicate copy of
licensed data lives in this module.

## Residual dependency & license inventory (criterion 6)

Ported and licensed: ASK engine code (Apache-2.0, headers preserved),
AOSP LatinIME wordlist (Apache-2.0, existing gate covers the file).

NOT ported yet — the honest list:

1. **Native binary-trie path** (`jnidictionaryv1/v2`, C++ `.so` +
   `BinaryDictionary`/`ResourceBinaryDictionary`): ASK's production
   main-dictionary format (`.dict` chunks). The spike loads the wordlist
   TEXT into the pure-Kotlin BTreeDictionary instead; the native trie
   (and its `getSuggestionsNative` scoring) is the largest remaining
   port and the main performance parity risk.
2. **User-dictionary storage** (`UserDictionary`, `AndroidUserDictionary`,
   `FallbackUserDictionary`, sqlite `WordsSQLiteConnection`,
   `AutoDictionary`, `AbbreviationsDictionary`): Android storage
   (ContentProvider/SQLiteDatabase). The engine defines the
   `EditableDictionary` SPI; hosts (or a follow-on android-storage module)
   provide implementations.
3. **Contacts dictionary** (content + rx): excluded by design
   (`EngineSettings.useContactsDictionary` exists so hosts can wire their
   own; the engine ships no contacts reader).
4. **Quick-text/emoji tags** (`TagsExtractor`): ASK quick-text addon
   surface, not prediction-engine core. Excluded.
5. **Prefs backup provider** (`NextWordPrefsProvider`): host backup
   concern.
6. **SuggestTest's Robolectric suite** (949 lines): the engine's unit
   tests are new and focused (typo correction, next-word, dictionary
   load); porting ASK's full behavioral matrix is part of cutover work.

## Remaining-work estimate (criterion 7)

- Native trie port + `.dict` reader + perf parity: the dominant item;
  ~1 focused week (CMake integration as an optional engine android
  variant, or a pure-Kotlin `.dict` reader measured against the native
  path).
- ASK-host cutover (consume artifact, delete copied code, port the full
  Robolectric matrix): ~2–3 days, gated on the native-trie decision.
- User/contacts storage SPI implementations per host: ~1 day per host.
- Locale parity (wordlist data work per language): data sourcing, not
  engine work; unbounded-by-engine, bounded-by-licenses.
- Release parity (signing/R8 for an android variant of the module if the
  native path ships): ~1 day after the trie decision.

## Status

Spike (issue #124): exit criteria 1 (artifact + composite-build
consumption proof in `consumer-proof/`), 2 (English dictionary
end-to-end through the engine — load, completions, typo correction,
next-word learning, all unit-tested), 6 (this inventory), 7 (this
estimate) discharged here; criteria 3–5 (Floris adapter, on-device
tests, latency/memory) are segment 2.

## Engine behavior a host adapter must know (found the hard way)

Corrections are **reachability + scoring**: the trie only returns words
whose characters match per-position typed-or-nearby codes (or
completions past the input end), and the Damerau-Levenshtein scorer
then ranks reachable near-words as fixes. `simulateTypedWord` feeds
primary codes only — it yields completions, never cross-position fixes.
A host adapter MUST supply nearby key codes per typed position
(ASK: `ProximityKeyDetector`) or corrections will silently disappear;
that requirement is on the P8 adapter and the residual-work list.
