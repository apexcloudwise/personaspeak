# Milestone 8 Plan — Release Readiness, Signing, Cert Pinning, Fail-Closed Build & Usefulness Proof

**Document Status: APPROVED.** Run `20260827T093000Z` at repo head `de797ba`.  
**Tracking Issue:** [apexcloudwise/personaspeak#114](https://github.com/apexcloudwise/personaspeak/issues/114)  
**Parent:** [apexcloudwise/personaspeak#38](https://github.com/apexcloudwise/personaspeak/issues/38) (Milestone 8, Phase-1 Exit)  
**Assignee:** `reicodes-pixelperfect`  
**Reviewers:** `opencode-glm-flash` / `seraph-pixelperfect`  

---

## 1. Context & Outcome

Milestone 8 is the final code milestone for PersonaSpeak. It transitions PersonaSpeak from a development integration prototype into a signed, reproducible, installable, and shippable Android APK release artifact (`v0.1.0` / `versionCode=1000`).

### Decisions of Record
- **M4 Formally Deferred**: As decided by repository owner `zaphodis42` on 2026-08-27 (agentchattr msg 1839), no real Anthropic API key will be provisioned in CI/repo. The mock-only ruling stands permanently. The credential-dependent device gates (#96/#89) remain deferred, not satisfied.
- **#111 Emulator Journey**: The live AVD emulator journey (Milestone 7 live receipt) is assigned separately to `opencode-glm-flash` under issue [#111](https://github.com/apexcloudwise/personaspeak/issues/111) and draft PR [#115](https://github.com/apexcloudwise/personaspeak/pull/115), building against `de797ba`.
- **Milestone 8 Final Gate**: The `v0.1.0` immutable annotated git tag cut in Slice B is strictly gated on #111 merging into `main`.

---

## 2. Slice Breakdown

Milestone 8 is structured into two verifiable slices:

```
+-----------------------------------------------------------------------------------+
| Milestone 8: Release Readiness                                                    |
+-------------------------------------------------+---------------------------------+
| Slice A: Build, Sign & Verification             | Slice B: Proof, CI & Tag Cut    |
| - v0.1.0 / versionCode 1000 configuration       | - Usefulness receipt (live/stub)|
| - Release signing & reproducible keystore path  | - Required CI status checks     |
| - Certificate fingerprint verification          | - Gated on #111 landing         |
| - Fail-closed active composition verification   | - Immutable annotated tag cut   |
| - R8 / ProGuard minification risk pass          | - Phase-1 exit demo sign-off    |
| - Dependency lock & reproducibility baseline     | - PR #114 closeout              |
+-------------------------------------------------+---------------------------------+
```

---

## 3. Slice A Scope & Architecture

### 3.1 Version Identity
- `applicationId`: `biz.pixelperfectstudios.personaspeak`
- `versionCode`: `1000`
- `versionName`: `0.1.0`
- Configured deterministically in `android/keyboard/ime/app/build.gradle` overriding upstream auto-version counters for the PersonaSpeak product APK.

### 3.2 Release Signing & Provisioning Path
- **Zero Checked-in Secrets**: No release keystores, `.jks`, `.keystore`, or password strings are checked into git.
- **Environment-Driven Configuration**:
  - `PERSONASPEAK_RELEASE_KEYSTORE`: Path to PKCS12 / JKS keystore file.
  - `PERSONASPEAK_RELEASE_KEYSTORE_PASSWORD`: Keystore protection password.
  - `PERSONASPEAK_RELEASE_KEY_ALIAS`: Signing key alias (defaults to `personaspeak` or `anysoftkeyboard`).
  - `PERSONASPEAK_RELEASE_KEY_PASSWORD`: Key alias password.
- **Developer & CI Fallback**: When release environment variables are not supplied, debug and local developer builds sign with the standard debug certificate (`~/.android/debug.keystore`), while release builds cleanly fail closed if release signing is explicitly requested without keystore credentials.
- **Reproducible Local Keystore Generator**: Script `android/scripts/generate-release-keystore.sh` to generate deterministic developer release test keystores out-of-tree.

### 3.3 Certificate Fingerprint Pinning
- Pinned SHA-256 certificate fingerprints for debug and official release signatures.
- Verified via `verify-release-signing.sh` inspecting signed APKs using `apksigner verify --print-certs`.

### 3.4 Fail-Closed Release Build (Active Composition Gate)
- **ROADMAP Gate Rule**: "The release build must be rejected if the active provider composition is fake/stub — test the active composition, not ban the class."
- `FakeProvider` remains completely legal as an explicit user-selected offline understudy.
- What is rejected: A default-active fake/stub provider or hardcoded stub active provider in release mode without user selection.
- Verified by `ReleaseActiveCompositionTest` ensuring `ProviderCatalog.all` offers real production providers (`openrouter`, `anthropic`, `openai-compat`) and that `ResolvingProvider` defaults to unconfigured state rather than hardcoding a mock provider ID.

### 3.5 R8 / ProGuard Minification Risk Pass
- Verify ProGuard rules retain:
  - Compose runtime & animation intrinsics (`androidx.compose.*`).
  - SnakeYAML deserialization classes (`biz.pixelperfectstudios.personaspeak.personas.*`).
  - DataStore preference protobuf serializers.
  - AnySoftKeyboard native JNI wrappers (`com.anysoftkeyboard.dictionaries.jni.*`).
- Documented in `docs/evidence/milestone-8/r8-minification-pass.md`.

### 3.6 Dependency Lock & Reproducibility Baseline
- Record runtime and compile classpath dependency tree in `docs/evidence/milestone-8/dependencies-lock.txt` and `dependencies-lock.json`.

---

## 4. Slice B Scope & Release Exit (Preview)

1. **Usefulness Receipt**:
   - Production path rewrite evaluation (with `openrouter` / `anthropic` or offline understudy) documenting single-mutation apply.
   - User-presentable error card rendering for rate limits / offline / missing keys (Stitch error states).
2. **CI Hygiene (#15/#16)**:
   - Branch protection / required check rule documentation ensuring PRs cannot merge without green CI.
3. **Immutable Tag Cut**:
   - `git tag -a v0.1.0 -m "Release v0.1.0 - PersonaSpeak"`
   - Gated on PR #115 (#111 emulator journey) landing into `main`.

---

## 5. Verification Gates

1. `./gradlew :ime:app:testDebugUnitTest`: 100% green unit & integration test pass.
2. `verify-milestone-8.sh`: Aggregates ASK closure, upstream ledger, signing checks, active-composition gate, R8 rules check, and receipt validation.
3. `verify-milestone-8-test.sh`: Contract test validating fail-closed positive and negative scenarios.
