# PHONE-ASSISTANT-003 — Semantic app bridge

Implemented on top of PHONE-ASSISTANT-002 on main. Existing local branches were already ancestors of main; the unpushed PHONE-ASSISTANT-002 commit was preserved.

## Authority and route order

Native → AppFunction → DirectAction → Intent → Accessibility → User.

Model reasoning never owns platform authority. Capability resolution precedes the existing SkillExecutionService / ToolTurn approval, resource leases and durable execution claim. Prepared operations are generation-bound. Native commands and deterministic sequences use this same authority without requiring an AGENTIC provider.

The portable SemanticAppActionBroker separates dispatch truth from effect truth. A timeout, crash or cancellation after a possible dispatch records UNKNOWN and cannot trigger a second route. Launching an Intent or returning app data is not proof of the requested effect. Explicit pre-dispatch rejection can select a weaker permitted route.

## Implemented behavior

- Current-app capability discovery, bounded ephemeral model tools and API 37 activity-scoped AppFunctions.
- DirectActions adapted to the same semantic capability model and invalidated with assistant activity handles.
- Fresh, unambiguous accessibility target tokens only when stronger semantic interfaces are unavailable. Protected authentication, CAPTCHA and permission steps require manual takeover.
- Short-lived deterministic references, volume follow-ups, targeted media pause and up to four resolved sequential commands. Expired or ambiguous references ask for clarification.
- SMS/message/email composition through SENDTO. Ambiguous contacts are resolved before opening a composer. No automatic delivery or broad SMS permissions.
- Official Internet and volume panels plus existing settings destinations.
- Existing Activity receipts, workflow projector and Skills capability summary. Overlay hide drops transient activity handles while preserving durable workflows; no background tree polling was added.

## Privacy and bounds

Capabilities are untrusted data. Discovery admits at most 64 functions per package, 16 current candidates and 12 model tools; descriptions are limited to 500 characters and parameters to 24. Dynamic names hash public capability identity and generation, never private arguments. Raw external descriptions do not become prompts or policies.

Message bodies, recipient details and external argument values remain in a process-local 120-second vault. Journal capsules contain field names, types, presence and lengths. Restart requires fresh input. Approval binds an exact capsule; loop protection uses conservative structural identity and a per-run operation cap, preventing a new call ID from bypassing duplicate protection without hashing private content.

Semantic receipts extend the existing AgentStep journal and Room serialization; no database table or migration is added. They record action, package, route, phase, dispatch/effect truth, typed failure and argument shapes. Private message, clipboard and share transcripts are masked in native requests.

## Limits and validation

AndroidX AppFunctions 1.0.0-alpha12 is isolated in host-android. Supported external argument schemas currently include flat String, Int, Long, Double and Boolean. Nested, array and URI/resource schemas are excluded rather than guessed or granted broad URI access. Older or unauthorized devices report capability unavailability and retain native execution.

Automated checks and APK verification are recorded in [acceptance](../testing/SEMANTIC-APP-OPERATOR-ACCEPTANCE.md). Physical AppFunction, DirectAction, accessibility, composer and wearable acceptance remains pending. No hardware route or message delivery is claimed.
