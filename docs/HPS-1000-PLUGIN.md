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
