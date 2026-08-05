# Dictionary source licenses

This manifest maps every dictionary build input actually selected by
`MakeDictionaryPlugin` for the English language pack to its source and
license. `android/scripts/verify-dictionary-licenses.sh` enforces the mapping
fail-closed: each selected input must have exactly one row here, and each row
must name a selected input.

Row format: `<path from android/keyboard> | <source> | <license>`.

addons/languages/english/pack/dictionary/en_wordlist.combined.gz | AOSP LatinIME | Apache-2.0
addons/languages/english/pack/dictionary/prebuilt/additionals.xml | AnySoftKeyboard | Apache-2.0
addons/languages/english/pack/dictionary/prebuilt/websites.xml | AnySoftKeyboard | Apache-2.0

## Intentionally excluded inputs

`dictionary/inputs/*` (the raw corpus and Wikipedia-derived text files) is
intentionally excluded from dictionary generation: the vendored snapshot does
not establish adequate per-corpus provenance for those files. The exclusion is
enforced by `ext.dictionaryTextInputsEnabled = false` in the English pack's
`build.gradle`, honored by `MakeDictionaryPlugin`. The vendored source files
remain in the tree untouched; they are simply not build inputs.

The three shipped sources above are also surfaced to users on the ASK
Additional Software Licenses screen reachable from settings.
