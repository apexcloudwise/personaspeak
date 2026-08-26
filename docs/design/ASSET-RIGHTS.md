# PersonaSpeak Asset Rights & Provenance Manifest

**Milestone:** Milestone 6 (Visual & Reach Fidelity)  
**Parent Issue:** [#38](https://github.com/apexcloudwise/personaspeak/issues/38)  
**Tracking Issue:** [#106](https://github.com/apexcloudwise/personaspeak/issues/106)  
**Status:** Authoritative release provenance artifact  
**Date:** 2026-08-27  

---

## 1. Overview & Policy

This document provides the exhaustive provenance, licensing, and redistribution rights determinations for all visual, typographic, and character assets in PersonaSpeak, satisfying the non-negotiable legal gates of Milestone 6.

### Non-Negotiable Asset Rules

1. **Fonts:** Bundled fonts must carry their full license text (e.g. SIL Open Font License 1.1) and attribution notices. When unbundled, the application strictly falls back to platform/system typography and explicitly records the fidelity status.
2. **Portraits & Personas:** No photographic portrait, broadcast still, or commercial actor likeness ships without recorded, verified redistribution rights. All bundled personas ship with rights-cleared Unicode emoji representations ("🎩", "🏛️", "🎯", "🎬") in place of third-party imagery.
3. **Icons & Glyphs:** All vector icons and typographic symbols must comply with Apache-2.0 or open-source font standards.
4. **Raster Assets:** Zero unapproved raster images (`.png`, `.jpg`, `.webp`) or remote mockups from exploratory design exports ship in first-party packages.

---

## 2. Bundled Persona Rights & Representation Matrix

| Persona Slug | Identity / Character | Intellectual Property & Rights Analysis | Shipped Representation | Clearance Status |
|---|---|---|---|---|
| `jeeves` | Reginald Jeeves | Fictional valet created by P.G. Wodehouse (1915). Literary public domain in US/UK for early works. | Unicode Emoji `🎩` (U+1F3A9, Top Hat) | **CLEARED** (Emoji representation; no broadcast/actor media) |
| `sir-humphrey` | Sir Humphrey Appleby | Fictional civil servant from BBC *Yes Minister* (1980, Antony Jay & Jonathan Lynn). Broadcast stills & Nigel Hawthorne likeness copyrighted by BBC/estates. Commercial redistribution rights not acquired. | Unicode Emoji `🏛️` (U+1F3DB, Classical Building) | **CLEARED** (Emoji representation; third-party photos excluded) |
| `dr-schultz` | Dr. King Schultz | Fictional bounty hunter from *Django Unchained* (2012, Quentin Tarantino / Columbia Pictures / TWC). Film stills & Christoph Waltz likeness copyrighted. Commercial redistribution rights not acquired. | Unicode Emoji `🎯` (U+1F3AF, Direct Hit) | **CLEARED** (Emoji representation; third-party photos excluded) |
| `amitabh-bachchan` | Amitabh Bachchan | Living Indian cinema actor and cultural icon. Distinct persona marked `real_person: true`. Commercial publicity rights & photographic likeness protected. Stylistic performance homage declared in persona `notes`. | Unicode Emoji `🎬` (U+1F3AC, Clapper Board) | **CLEARED** (Emoji representation; photographic likeness excluded) |

---

## 3. Typography & Font Licensing

### 3.1 Design Typography Tokens

PersonaSpeak's design specification defines two open-source font families for UI styling:
- **Heading / Title Display:** **Outfit** (Designed by Onsen / Rodrigo Fuenzalida, SIL Open Font License 1.1)
- **Body & Interactive UI:** **Inter** (Designed by Rasmus Andersson, SIL Open Font License 1.1)

### 3.2 System Fallback Policy
In accordance with the Stitch screen contract (`docs/superpowers/specs/2026-07-22-stitch-screen-contract.md`), when binary TTF font files are not bundled in application assets, the UI uses the standard Compose Material 3 platform font stack (`FontFamily.Default` / system sans-serif) without breaking layout bounds or accessibility scaling.

### 3.3 SIL Open Font License (OFL) Version 1.1

```text
SIL OPEN FONT LICENSE Version 1.1 - 26 February 2007
-----------------------------------------------------------
PREAMBLE
The goals of the Open Font License (OFL) are to stimulate worldwide
development of collaborative font projects, to support the font creation
efforts of academic and linguistic communities, and to provide a free and
open framework in which fonts may be shared and improved in partnership
with others.

The OFL allows the licensed fonts to be used, studied, modified and
redistributed freely as long as they are not sold by themselves. The
fonts, including any derivative works, can be bundled, embedded, 
redistributed and/or sold with any software provided that any reserved
names are not used by derivative works. The fonts and derivatives,
however, cannot be released under any other type of license. The
requirement for fonts to remain under this license does not apply
to any document created using the fonts or their derivatives.

PERMISSION & CONDITIONS
Permission is hereby granted, free of charge, to any person obtaining
a copy of the Font Software, to use, study, copy, merge, embed, modify,
redistribute, and sell modified and unmodified copies of the Font
Software, subject to the following conditions:

1) Neither the Font Software nor any of its individual components,
in Source or Binary forms, may be sold by itself.

2) Modified or unmodified copies of the Font Software may be bundled,
redistributed and/or sold with any software, provided that each copy
contains the above copyright notice and this license. These can be
included either as stand-alone text files, human-readable headers or
in the appropriate machine-readable metadata fields within text or
binary files as long as those fields can be easily viewed by the user.

3) No Modified Version of the Font Software may use the Reserved Font
Name(s) unless prominent written permission is granted by the
corresponding Copyright Holder. This restriction only applies to the
primary font name as presented to the users.

4) The name(s) of the Copyright Holder(s) or the Author(s) of the Font
Software shall not be used to promote, endorse or advertise any
Modified Version, except to acknowledge the contribution(s) of the
Copyright Holder(s) and the Author(s) or with their explicit written
permission.

5) The Font Software, modified or unmodified, in part or in whole,
must be distributed entirely under this license, and must not be
distributed under any other license. The requirement for fonts to
remain under this license does not apply to any document created
using the Font Software.

TERMINATION
This license becomes null and void if any of the above conditions are
not met.

DISCLAIMER
THE FONT SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO ANY WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT
OF COPYRIGHT, PATENT, TRADEMARK, OR OTHER RIGHT. IN NO EVENT SHALL THE
COPYRIGHT HOLDER BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
INCLUDING ANY GENERAL, SPECIAL, INDIRECT, INCIDENTAL, OR CONSEQUENTIAL
DAMAGES, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
FROM, OUT OF THE USE OR INABILITY TO USE THE FONT SOFTWARE OR FROM
OTHER DEALINGS IN THE FONT SOFTWARE.
```

---

## 4. UI Glyphs & Icons Licensing (Apache-2.0)

Interactive UI glyphs across `:personaspeak-ui` (such as navigation arrow `←`, dismiss `✕`, dropdown chevron `⌄`, refresh `↻`, and bullet `•`) are rendered using standard platform Unicode text glyphs and Material Design iconography.

```text
Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## 5. Upstream Inherited Assets

AnySoftKeyboard base textures, dictionary assets, and layout drawables are governed by:
- `android/keyboard/LICENSE` (Apache-2.0)
- `android/keyboard/DICTIONARY-LICENSES.md` (AOSP LatinIME / AnySoftKeyboard dictionary sources)
- `android/keyboard/UPSTREAM.md` (Pristine snapshot provenance)
