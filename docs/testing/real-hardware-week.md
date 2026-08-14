# 7-Day Real-Hardware TestFlight

## Objective
This RC focuses on real-world edge cases with physical hardware. We need to discover the ugly problems that code review cannot reveal.

## Daily Focus Areas

### Day 1: Discovery & Handshake Stability
- Connect and disconnect HeyCyan repeatedly.
- Walk out of BLE range and walk back in.
- Restart the app while glasses are in use.

### Day 2: Cognitive Routing
- Execute varied semantic intents (media, tasks, questions).
- Switch between Local Inference (LlamaCpp) and Cloud Reasoning (OpenRouter).
- Observe intent rejections for unsupported actions.

### Day 3: Power & Battery Realities
- Track battery drain on Android Host.
- Track battery drain on HeyCyan glasses.
- Verify battery reporting on the Intelligence UI matches hardware reality.

### Day 4: Wear & Tear
- Wear glasses all day in various temperatures.
- Keep app running in background all day.
- Test connection stability in crowded RF environments (e.g., coffee shops, train stations).

### Day 5: State Synchronization
- Verify capability sync accurately reflects hardware capabilities.
- Toggle Auto-connect off and on in Preferences to verify persistence.

### Day 6: Edge Cases & Error Handling
- Simulate network failure during a Cloud Reasoning intent.
- Try connecting to dead glasses.
- Enter API keys that are invalid or expired.

### Day 7: Wrap Up & Review
- Collect all logs.
- Document any crashed cognitive sessions.
- Review UX flows for HeyCyan device controls and Intelligence hub.

## Known Limitations
- Background execution requires battery optimizations to be disabled manually.
- Some HeyCyan semantic mapping is pending. Unsupported operations will fail gracefully.
