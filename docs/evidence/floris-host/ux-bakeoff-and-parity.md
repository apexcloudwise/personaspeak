# FlorisBoard Second Host — UX Bake-off Protocol & Host-Parity Raw Results

**Document Status: QUALIFIED (agent-executable portion; owner judge portion PENDING — loud skip below)**  
**Scope:** ADR-0010 P6 — blinded UX bake-off protocol + latency/memory parity vs the ASK host  
**Fixture:** M2_Qual_Fixture, snapshot `m2_pristine`, single boot 2026-09-04 (both hosts measured on the same boot)  
**Builds:** ASK debug APK (`6cb7892`-era tree, 61.2 MB), Floris debug APK (`6cb7892`-era tree, 37.8 MB; release/R8 = 15.0 MB per P3)

---

## 1. Blinding statement (the honest limit)

**A truly blinded comparison of these two hosts is not achievable by the executing agent, and only partially achievable at all.** The hosts' keyboard chrome differs visibly (FlorisBoard's Smartbar and theme system vs ASK's classic rows), so a judge who has seen both keyboards once can identify which is which on sight. The protocol below therefore blinds *order and assignment*, not appearance: the judge rates each host in separate sittings without being told which sitting is which host, and ratings are sealed before unblinding. Single-agent execution cannot provide a human judge at all; the agent-executable portion of P6 (§3) is what was actually run, and the judge portion is left staged for the owner.

## 2. Owner-run bake-off protocol (staged, not executed)

1. **Preparation (agent or second person):** on the owner's device or the fixture, install both debug APKs. Assign labels A/B by coin flip; record the mapping in a sealed note. Do not tell the judge.
2. **Sittings (owner, separate hours or days):** per sitting, set one host as default (the preparator does this), then: type a 40-word message; rewrite one sentence with persona+review; apply one rewrite; correct a typo mid-word; switch persona; open the settings surface. Rate 1–5: typing feel, suggestion relevance (Floris has none — rate the absence honestly), rewrite flow, visual coherence, overall.
3. **Unblinding:** after both sittings' ratings are written down, open the sealed note. The protocol's value is that the ratings predate the label reveal.
4. **Imperfection, restated:** appearance defeats full blinding; the randomized order and sealed ratings are the achievable controls. If the owner recognizes a host mid-sitting, that sitting's rating is still recorded, flagged as recognized.

## 3. Host-parity raw results (executed 2026-09-04)

Both hosts ran one identical scripted session on the same fixture boot: enable → set → Settings search editor → type `Tea at six.` through real key taps (per-host calibrated pins) → Rewrite → settle → Apply. Session success proven by editor readback equal to the FakeProvider candidate on both hosts. `dumpsys meminfo` captured after each apply.

### 3.1 Memory (dumpsys meminfo, KB)

| Metric | ASK host | Floris host | Δ (Floris − ASK) |
| :--- | ---: | ---: | ---: |
| **TOTAL PSS** | 100,635 | 150,279 | **+49,644 (+49%)** |
| TOTAL RSS | 194,108 | 247,304 | +53,196 |
| Native Heap (PSS) | 24,417 | 28,986 | +4,569 |
| Dalvik Heap (PSS) | 12,461 | 17,889 | +5,428 |
| `.apk mmap` (PSS) | 39,666 | 672 | −38,994 |

Reading: the Floris host carries **~49% more total PSS** after an identical session. Most of the delta is live heap (Compose + jetpref + theme engine), partly offset by ASK's enormous `.apk mmap` (its 61 MB APK pages in ~39 MB of PSS where Floris's 37 MB APK pages almost none lazily). Private Dirty (the "owned exclusively" figure): ASK ≈ 44 MB vs Floris ≈ 52 MB — the exclusive-memory gap is ~+18%, smaller than the PSS gap but still the Floris host paying for its richer chrome.

### 3.2 Artifact size

| Artifact | ASK | Floris |
| :--- | ---: | ---: |
| Debug APK | 61.2 MB | 37.8 MB |
| Release APK (R8) | — (not rebuilt this run) | 15.0 MB (P3 proof) |

### 3.3 Latency

No honest agent-executable latency number was captured this run: `input tap`-driven timing on the fixture measures the harness, not the host (tap dispatch ≈10 ms, recomposition lands async), and logcat-timestamp scraping is host-dependent (Flog formats differ from ASK's logger), which would compare logging pipelines rather than keyboards. The owner-run protocol (§2) collects perceived-latency ratings; a future instrumented run (systrace/perfetto on both hosts, identical editor, marker spans) is the mechanical path and is noted as remaining work.

### 3.4 Known-session caveats (recorded, not smoothed over)

- Single sample per host; run-to-run variance on the software-rendered fixture is real (~few MB PSS).
- The ASK process was resident ~2 minutes longer than the Floris process at capture time (it ran first); older processes tend to accumulate, which if anything **understates** the Floris delta.
- Both builds are debug (debuggable, Flog active in Floris) — matching how every qualification journey runs, but not release posture; P3's release build exists for the Floris host only.
- The two hosts' feature sets differ (ASK ships suggestions; Floris does not) — the memory comparison is of the keyboards as they are, not as equal-feature products. A suggestion engine (0.6 or the P7/P8 extraction) would add to the Floris side.

### 3.5 Reproduction

Exact commands recorded in the P6 working notes: `adb install -r <host apk>` → `ime enable/set <host component>` → force-stop Settings → `am start -a android.settings.SETTINGS` → `input tap 540 659` → per-host key-tap sequences (ASK pins: `adb_harness.py` `ASK_KEY_COORDS`; Floris pins: `floris_harness.py` `FLORIS_KEY_COORDS`) → row taps (ASK `REWRITE_TAP/APPLY_TAP`; Floris `FLORIS_REWRITE_TAP/FLORIS_APPLY_TAP` at layout positions, per #131) → `dumpsys meminfo <package>`.

## 4. Verdict of the executed portion

Memory parity: **the Floris host costs ~49% more total PSS / ~18% more private memory after an identical session on the pinned fixture** — the price of Compose chrome and jetpref over ASK's older toolkit, partially offset by lazier APK paging. This is raw evidence for ADR-0010's promotion evaluation, not a verdict: a default-keyboard decision weighs the delta against the UX findings the owner-run bake-off (§2) and FlorisBoard 0.6's suggestion engine would bring.

## 5. Non-author review

Non-author review skipped: no non-author agent available at execution time (owner AFK, single-agent run). Owner review pending — and the bake-off judge seat is the owner's by design (§1).
