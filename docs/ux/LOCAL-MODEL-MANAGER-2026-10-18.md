# Local model manager repair — source status

User reports: downloaded/available confusion, failed LiteRT imports, Hugging Face token application, and no tool-capable models visible.

## Source findings and changes

- Import Result was discarded in IntelligenceViewModel. Import now reports progress and actionable failures, uses OpenDocument access, serializes imports, checks reported length/free space, observes cancellation, cleans temporary files and allows a damaged existing copy to be replaced when it is not resident or downloading. Duplicate valid artifacts retain canonical identity. Import does not assert native runtime compatibility.
- Manager token handling did not trim pasted whitespace; no general Apply token path existed. A read token can now be applied or cleared for the app session. It is held in memory, not stored on disk or in WorkManager data. Download and Hub metadata authentication is restricted to HTTPS huggingface.co. Applying a token validates its shape; actual account/model access is checked by the download. Publisher license acceptance remains a separate explicit checkbox. Restarting the process clears the token.
- File inventory changes now explicitly refresh presentation. The list has on-device/available/in-progress/attention filters, physical-file counts and distinct verified/unverified/damaged labels. Unknown/failed inventory never renders a download as known absent. Import and refresh are primary controls rather than hidden diagnostics.
- The Tools filter inspected only supportsTools, whose contract means artifact-specific native tool evidence. It now includes the existing Halo prompt tool-loop candidates. The shared downloadable catalogue projects that existing loop eligibility too. Native tool support flags and evidence are unchanged; no model is labelled native-tool-proven by family name or download alone. Hub discoveries explicitly configured without a tool protocol remain excluded.

## Not established

No tests, builds, CI or deployment were run for this source batch, following issue 34's coding-only instruction. Physical import, gated-download access, runtime load/generation and tool-loop completion still require verification on the actual phone. These changes do not establish that any newly imported or downloaded model supports native tool calls, vision or reliable action execution.
