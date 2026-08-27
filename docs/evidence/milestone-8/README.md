# Milestone 8 — Release Readiness Evidence

**Status: QUALIFIED (Slice A Build & Signing Baseline).**  
**Milestone:** Milestone 8 ([#114](https://github.com/apexcloudwise/personaspeak/issues/114))  
**Evidence Class:** `build_and_signing_harness`  
**Run ID:** `20260827T093000Z-m8-slice-a`  

---

## 1. Executive Summary

Milestone 8 Slice A establishes the complete release packaging, signing configuration, version identity, fail-closed active-composition gate, and build reproducibility baseline for PersonaSpeak `v0.1.0`.

### Qualified Invariants
1. **Version Identity**: Configured `versionCode=1000`, `versionName="0.1.0"`, and `applicationId="biz.pixelperfectstudios.personaspeak"` in `:ime:app`.
2. **Release Signing Configuration**: Zero checked-in secrets in repo; environment-driven signing configuration reading `PERSONASPEAK_RELEASE_KEYSTORE`, passwords, and alias. Reproducible local developer keystore generator in `android/scripts/generate-release-keystore.sh`.
3. **Fail-Closed Active-Composition Gate**: Enforced via `ReleaseActiveCompositionTest` ensuring the release build rejects default-active fake/stub composition while permitting user-selected offline `FakeProvider`.
4. **R8 & Minification Risk Review**: Audited in `docs/evidence/milestone-8/r8-minification-pass.md` confirming zero reflection/obfuscation reachability regressions across Compose, SnakeYAML, and DataStore.
5. **Dependency Lock Baseline**: Pinned runtime classpath dependency tree in `docs/evidence/milestone-8/dependencies-lock.txt`.

---

## 2. Slice B Gating

Slice B (final ship proof, usefulness receipt, CI required-checks transition, and `v0.1.0` immutable annotated git tag) is gated on PR #115 (the #111 live emulator journey run by `opencode-glm-flash`) merging into `main`.
