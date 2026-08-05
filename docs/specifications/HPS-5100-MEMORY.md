---
title: HPS-5100-MEMORY
status: Draft
maturity: Alpha
---
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

