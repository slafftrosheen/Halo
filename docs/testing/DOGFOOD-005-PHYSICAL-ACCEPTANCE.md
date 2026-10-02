# DOGFOOD-005 Physical Acceptance Protocol

Execute the following test cases on physical hardware:

### Test 1 — Native Speed
1. Say/type: *"Open Spotify"*
2. Verify telemetry:
   - `HALO_NATIVE_ACTION_ROUTE`
   - `HALO_NATIVE_ACTION_TIMING ... totalMs=<300ms`
   - Android application launch occurs immediately without provider call or secondary confirmation dialog.
3. Say/type: *"Set a timer for two minutes"*
4. Verify: Immediate timer set in clock app without provider call or duplicate confirmation.

### Test 2 — Sensitive Authorization
1. Say/type: *"Text John that I'll be late."*
2. Verify: Bounded recipient/body resolution occurs, presenting the composer/approval sheet before sending.

### Test 3 — Vision Pipeline
1. Attach a photo in Home screen.
2. Ask: *"What is in this image?"*
3. Verify telemetry:
   - `HALO_VISION_BEGIN`
   - `HALO_MEDIA_CONTEXT_SELECTED`
   - `HALO_MEDIA_PREPARED`
   - Visual provider response committed and displayed.

### Test 4 — UI Purity
1. Navigate repeatedly through screens: `Home`, `Voice`, `Skills`, `Vision Lab`, `Automations`, `System Check`.
2. Inspect Logcat for StrictMode violations.
3. Verify: 0 Halo-owned main-thread filesystem reads.
