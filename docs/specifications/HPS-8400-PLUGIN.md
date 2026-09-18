---
title: HPS-8400-PLUGIN
status: Draft
maturity: Alpha
---

> Status: DESIGN TARGET. Classified 2026-09-18.
>
> Requirements, examples and proposed behavior; not an implementation or verification report. Read present-tense guarantees below as intended acceptance criteria, not achieved properties. Current code and actual wiring determine behavior. Any previous ACTIVE/authority wording applies only to historical design intent.
>
> [Current reference](../../CURRENT-STATE.md).

# HPS-1000: Plugin Contract

Plugins are first-class citizens. No magic.

## Plugin Manifest
```yaml
id:
version:
permissions:
capabilities:
commands:
settings:
minimum_runtime:
author:
license:
```

## Plugin API
Plugin receives:
`Runtime -> Context -> Commands -> Queries -> Events`
Never Android Context. Never Compose.
