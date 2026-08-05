# Database Specification

Treat Room (SQLite) as persistence, not business logic.
Repositories expose domain models only. Entities never leak into the UI or engines.

## Core Tables
* **Devices**: Known hardware, last seen, capabilities.
* **Providers**: Configured AI backends and keys.
* **Plugins**: Installed plugins and granted permissions.
* **Automations**: Saved IF/THEN rules.
* **Memories**: Short, medium, and long-term storage vectors.
* **History**: Timeline of all events and interactions.
* **Settings**: Immutable configuration states.

## Principles
* **Exportable**: Everything must be exportable to JSON/CSV.
* **Local Only**: No automatic cloud syncing.
* **Encrypted**: Optional SQLCipher layer for sensitive tables.
