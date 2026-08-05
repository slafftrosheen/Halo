# Architectural Decision Records (ADRs)

This log records major architectural decisions for the Halo project.

## ADR-001: Memory Moved to Runtime
**Date**: 2026-08-05
**Decision**: The memory domain was moved from an engine to untime:cognition.
**Reason**: Memory is a domain concept that engines manipulate, not an engine itself.
