# Vision Lab acceptance

Status: LIVE PROVIDER AND PHYSICAL ACCEPTANCE PENDING. Automated wire and policy tests use synthetic media and intercepted HTTP.

1. With cloud images disallowed, select a gallery/camera/HeyCyan image and a remote candidate. Verify POLICY rejection before media acquisition or HTTP submission.
2. Allow cloud images explicitly, retaining screen/object policy restrictions. Select concrete OpenAI, Gemini and OpenRouter models. Run the synthetic probe, recording candidate ID, evidence timestamp and recognition success. openrouter/free must remain ineligible.
3. Compare the same selected image across verified candidates. Confirm one preparation, common dimensions/MIME/bytes and separate provider timings. No private text, base64 or full response may enter durable metrics/history.
4. Repeat with phone camera permission denied/granted, a fresh wearable thumbnail, a current assistant screenshot and an expired/hidden assistant session. Expired context must not be submitted.
5. Verify no models are silently substituted and no CHAT binding changes. Image-plus-tools input must still select VISION. Text-only LiteRT must offer Vision setup rather than execute image input.
6. Exercise authentication, rate limiting, server failure, timeout, malformed response and content mismatch. Confirm accurate stages, no probe evidence on failure and retained selected image after synthetic probe cleanup.
7. Only when a verified supportsVision=true LiteRT artefact exists: install verified bytes/hash, run an on-device synthetic probe, verify image backend and actual vision output. Current text models do not satisfy this test.

Record real model IDs and results here when performed. Never treat a fake successful HTTP body as live capability evidence.

## Automated result

The complete requested suite passes: 610 tests, no failures/errors/skips, and a successful debug APK build. See the [runtime validation record](../engineering/PHONE-ASSISTANT-002-HOST-FIRST-RUNTIME.md#automated-validation--2026-10-01) for commands, coverage limits and APK hash. Physical and live-provider results remain pending; no device is connected over ADB.
