# Missing model setup skill manifest — #39 / #43

Phone at host-localization app SHA5a32a7d7cc4a2f1a6b25a482e08177abfd825a9d25c88b1ee324f57bad1c075b exposed production startup failure before any intent fixture case: BuiltInSkillManifestLoader.load rejected halo.models because its runtime descriptor had no packaged asset. Preserve fail-closed validation; do not hide the missing asset or create fallback registry state.

- [x] Add the missing version2 bundled manifest with the existing five operations and exact runtime risk classifications, including destructive rollback. No permission or operation expansion.
- [x] Add raw/default/merged manifest parity regression. Include the existing all-default-ID packaging guard in the focused gate.
- [x] Independent source review, then combined focused gate with complete erased-sketch source and matching APK packaging once.
- [x] Install/readback APK identities, rerun native startup/intent probe and retain initial failure evidence; publish scoped result without model-quality promotion.

The first standalone Ink gate was stopped at task-graph calculation to include this newly discovered startup blocker before further compilation. This was intentional cancellation, not a Kotlin/test failure. Native locale visibility failure is separate and under observation using unchanged installed fixture.
