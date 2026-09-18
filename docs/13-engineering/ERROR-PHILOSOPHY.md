> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# HPS-1400: Error Philosophy

No stack traces shown to the user. Every error must answer:
1. What happened?
2. Why?
3. Can Halo recover?
4. What can user do?
5. Can developer inspect?

Example:
`Couldn't reach provider. Reason: No internet. Recovery: Using local model. Inspect: Details >`
