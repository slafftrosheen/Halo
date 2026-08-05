---
title: HPS-0100-REPOSITORY
status: Draft
maturity: Alpha
---
# HPS-101: Repository Specification

## Purpose
Defines the repository layout. No developer may invent folders. No module may exist without documentation.

## Root Layout
```text
halo/
  .github/
  docs/
    architecture/
    design/
    sdk/
    rfcs/
  samples/
  tools/
  scripts/
  app/
  core/
  engines/
  drivers/
  providers/
  plugins/
  libraries/
  testing/
  benchmark/
```

## Module Naming
Always semantic: `halo-core`, `halo-runtime`, `halo-engine-memory`, `halo-provider-openai`, `halo-driver-heycyan`, `halo-plugin-calendar`.
Never arbitrary.

## Documentation Layout
Every folder begins with a `README.md`.

## Pull Request Rules
Every PR must include:
* Documentation
* Tests
* Changelog
* Screenshots (UI)
* Benchmarks (performance changes)

No exceptions.

