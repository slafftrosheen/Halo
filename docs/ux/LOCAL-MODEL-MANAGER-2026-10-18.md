# Local model manager repair — source status

User reports: downloaded/available confusion, failed LiteRT imports, Hugging Face token application, and no tool-capable models visible.

## Source findings and changes

- Import Result was discarded in IntelligenceViewModel. Import now reports progress and actionable failures, uses OpenDocument access, serializes imports, checks reported length/free space, observes cancellation, cleans temporary files and allows a damaged existing copy to be replaced when it is not resident or downloading. Duplicate valid artifacts retain canonical identity. Import does not assert native runtime compatibility.
- Manager token handling did not trim pasted whitespace; no general Apply token path existed. A read token can now be applied or cleared for the app session. It is held in memory, not stored on disk or in WorkManager data. Download and Hub metadata authentication is restricted to HTTPS huggingface.co. Applying a token validates its shape; actual account/model access is checked by the download. Publisher license acceptance remains a separate explicit checkbox. Restarting the process clears the token.
- File inventory changes now explicitly refresh presentation. The list has on-device/available/in-progress/attention filters, physical-file counts and distinct verified/unverified/damaged labels. Unknown/failed inventory never renders a download as known absent. Import and refresh are primary controls rather than hidden diagnostics.
- Comparison matrices, audio/vision labs, detailed compute advice and GPU/developer probes are grouped under Advanced. Import remains a primary control.
- The Tools filter inspected only supportsTools, whose contract means artifact-specific native tool evidence. It now includes the existing Halo prompt tool-loop candidates. The shared downloadable catalogue projects that existing loop eligibility too. Native tool support flags and evidence are unchanged; no model is labelled native-tool-proven by family name or download alone. Hub discoveries explicitly configured without a tool protocol remain excluded.

## Original source-only handoff

No tests, builds, CI or deployment were run for this source batch, following issue 34's coding-only instruction. Physical import, gated-download access, runtime load/generation and tool-loop completion still require verification on the actual phone. These changes do not establish that any newly imported or downloaded model supports native tool calls, vision or reliable action execution.

## Subsequent authorized phone verification

The user's later request explicitly authorized building, installation and physical testing. On 2026-10-18, the connected Redmi accepted a data-preserving debug update. Android's real Downloads picker successfully imported `gemma-4-tiny-random.litertlm` (25,907,888 bytes). Original and imported SHA-256 both equal `d0c6bec1f1b8b72a05eb99e9f7930a57f14bf3fa8bc905367381f1855e921bf0`; the original remains in Downloads. Inventory changed from 16 verified files to 17 present / 16 verified. Import is not runtime compatibility or trained-model quality proof. Two larger `.pending-…` files were left untouched.

Three physical instrumentation checks passed: real installed artifact import through a content URI and duplicate detection, invalid import cleanup, and session token trimming/apply/clear/header-injection rejection. A separate explicit Qwen 3 0.6B CPU probe at context 1024 passed raw generation and Halo provider verification. Authenticated Hugging Face account access, gated licenses and native tool-call completion remain unverified.

Phone inspection also found imported-card calibration details outside Advanced and a provider setup list blocked behind local fingerprint scans. Follow-up fixes move imported calibration/developer controls under Advanced and publish provider configuration before inventory refresh. The blocked-refresh regression test first reproduced the problem with a timeout. Final build/device results, including the passing repeat on the final APK, are recorded in `docs/testing/HALO-DEVKIT14-PHONE-2026-10-18.md`.
