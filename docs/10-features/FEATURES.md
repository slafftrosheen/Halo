# Feature Specifications

Each major feature in Halo receives a dedicated specification to outline its requirements, UI, and database needs before implementation.

## Features List

### 1. Memory Engine
* **Purpose**: Persist user facts and interaction history.
* **Requirements**: Searchable, Editable, Deletable, Local.
* **Database**: `Memories` table with vector embeddings (optional).
* **Voice Flow**: "Remember that..." -> Intent -> Memory Engine -> Store -> Confirm.

### 2. Automation Engine (Upcoming)
* **Purpose**: Execute workflows without user voice interaction.
* **Requirements**: IF/THEN rules based on Context (Time, Location, BLE).
* **Database**: `Automations` table.

### 3. Notification Summarizer (Upcoming)
* **Purpose**: Intercept and summarize phone notifications.
* **Requirements**: Android Notification Listener permission.
* **Voice Flow**: Intercept -> Summarize (LLM) -> Deliver to Wearable.
