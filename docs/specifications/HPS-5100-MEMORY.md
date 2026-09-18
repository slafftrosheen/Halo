---
title: HPS-5100-MEMORY
status: Draft
maturity: Alpha
---

> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# HPS-500: Memory Specification

Halo is not just chat. Halo has four memory types:

1. **Ephemeral**: Lives 30 seconds (Speech buffers, temporary OCR). Destroyed automatically.
2. **Session**: Lives until conversation ends. Deleted afterwards.
3. **Persistent**: Only stored if user approves (e.g., "Remember this").
4. **Knowledge**: Imported data (PDFs, Manuals, Git repositories).

## Memory Object
`UUID, Title, Content, Created, Updated, Tags, Embedding, Source, Trust, Visibility, Encryption`

## Search Pipeline
`Voice -> Intent -> Embedding -> Vector Search -> Ranking -> Memory -> Response`
Everything inspectable.
