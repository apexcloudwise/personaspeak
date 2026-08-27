# Milestone 8 Slice B — CI Hygiene & Required Checks Audit

**Document Status: QUALIFIED (Workflow Pipeline Consolidated; Branch Protection Configuration Documented & Pending Admin Activation).**  
**Issues:** [#15](https://github.com/apexcloudwise/personaspeak/issues/15), [#16](https://github.com/apexcloudwise/personaspeak/issues/16), [#114](https://github.com/apexcloudwise/personaspeak/issues/114)  

---

## 1. Executive Summary & Ground Truth

Issues #15 and #16 identified that GitHub Actions CI checks on repository `apexcloudwise/personaspeak` were originally advisory (in the `nomain` ruleset), allowing PRs to merge with unfinished or failing checks.

In Milestone 8 Slice B:
1. **CI Pipeline Consolidation in Code**:
   - `.github/workflows/ci.yml` is consolidated with all milestone verification gates (`verify-milestone-2.sh`, `verify-milestone-4.sh`, `verify-milestone-7.sh`, `verify-milestone-8.sh`) and contract test suites.
2. **Branch Protection & Ruleset Status**:
   - Live GitHub API query (`gh api repos/apexcloudwise/personaspeak/rulesets`) reports active rulesets `evidence-archive` and `nomain` without `required_status_checks` configured.
   - Updating GitHub repository rulesets requires admin permissions (which are reserved for repository owner `@zaphodis42`).
   - **Target Ruleset Policy**:
     - `PATCHNOTES.md touched`
     - `Validate personas & smoke-test CLI`
     - `M2 device qualification suite`
     - `Milestone 2 gate` (runs full test matrix and milestone verifiers)
   - **Status**: Workflow pipeline consolidated in git; ruleset enforcement is documented and pending repository owner/admin activation.
3. **Release Tag Gating**:
   - The final `v0.1.0` immutable release tag cut will occur once the admin applies the required status checks ruleset.
