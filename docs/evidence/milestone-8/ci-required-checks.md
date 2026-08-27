# Milestone 8 Slice B — CI Hygiene & Required Checks Audit

**Document Status: QUALIFIED.**  
**Issues:** [#15](https://github.com/apexcloudwise/personaspeak/issues/15), [#16](https://github.com/apexcloudwise/personaspeak/issues/16), [#114](https://github.com/apexcloudwise/personaspeak/issues/114)  

---

## 1. Executive Summary

Issues #15 and #16 identified that GitHub Actions CI checks on repository `apexcloudwise/personaspeak` were originally advisory (in the `nomain` ruleset), allowing PRs to potentially merge with unfinished or failing checks.

In Milestone 8 Slice B:
1. **CI Pipeline Consolidation**: `.github/workflows/ci.yml` is consolidated with all milestone verification gates (M2, M4, M7, M8) and contract test suites.
2. **Branch Protection & Required Checks Policy**:
   - The required checks policy for `main` enforces:
     - `PATCHNOTES.md touched` (fails closed if patch notes are missing)
     - `Validate personas & smoke-test CLI`
     - `M2 device qualification suite`
     - `Milestone 2 gate` (runs full test matrix, M4/M7/M8 gates, and verifies canonical APK sha256)
3. **Audit Invariant**:
   - No red or unverified PR can be merged into `main` prior to release tag cut.
