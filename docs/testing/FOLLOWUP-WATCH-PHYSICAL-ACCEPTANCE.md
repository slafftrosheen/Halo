# Follow-up Watch Physical Acceptance Protocol

Execute the following test cases on physical hardware:

### Test A — Network Watch
1. Turn off Wi-Fi and Mobile Data.
2. Say: *"Tell me when internet is back."*
3. Verify: Watch created deterministically (`halo.followups` skill) without LLM call.
4. Turn Wi-Fi on.
5. Expected: Notification or Home pending card appears ("Ready to continue · Network is available again").

### Test B — Device Reconnect
1. Turn off HeyCyan smart glasses.
2. Say: *"Tell me when my glasses reconnect."*
3. Turn glasses back on.
4. Expected: Single follow-up notification when device presence state transitions to connected.

### Test C — App Foreground
1. Say: *"Tell me when Spotify opens."*
2. Launch Spotify manually.
3. Expected: Single follow-up notification ("Spotify is open").

### Test D — Provider Recovery
1. Disable a configured cloud provider.
2. Choose *"Let me know when it's available again."*
3. Restore network/provider state.
4. Expected: Notification "Provider available [Retry]". Prompt is not sent automatically.

### Test E — Process Death & Reboot
1. Create a network follow-up watch ("Tell me when internet is back").
2. Kill the app process or reboot phone.
3. Verify: Watch survives reboot in `ACTIVE` state. Baseline state does not falsely fire transition-only watches.
4. Toggle network state: watch fires exactly once.
