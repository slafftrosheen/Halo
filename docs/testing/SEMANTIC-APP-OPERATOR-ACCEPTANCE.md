# Semantic app operator acceptance

Status: IMPLEMENTED / AUTOMATED VERIFIED / DEBUG APK BUILT. Physical acceptance pending.

2026-10-02: full regression suite plus a final rerun of app, cognition and host-android passed. 639 tests, zero failures, errors or skips. App assembleDebug succeeded at compile/target API 37. Host graph audit: 31 enabled skills, 86 tools, zero orphan, unresolved, duplicate or permission-mismatched entries. git diff --check passed. No ADB device was connected; physical outcomes are not claimed.

Failures found during implementation were corrected: alpha12 API signatures, dependency graph type inference, legacy speech parser compatibility, descriptor ownership expectations, Unicode receipt expectation and LiteRT MockWebServer teardown cancellation.

## Automated coverage

SemanticAppActionBrokerTest; AppOperationRoutePrecedenceTest; AppOperationNoDuplicateFallbackTest; ExternalAppFunctionCapabilityIndexTest; ExternalAppFunctionMetadataSanitizationTest; AppFunctionGenerationInvalidationTest; ActivityScopedAppFunctionTest; DirectActionSemanticAdapterTest; InteractionReferenceResolverTest; InteractionReferenceExpiryTest; DeterministicCommandSequenceTest; SemanticOperationReceiptTest; ManualTakeoverPolicyTest; ExternalToolExposureBoundsTest; AppFunctionErrorMappingTest; MessagingComposeIntentTest; SemanticOperationJournalCodecTest.

Additional checks cover old Android support, private capsule repeat identity, retained ambiguous contact drafts, actual shared native command execution and legacy speech parsing. The regression suite includes app, host-android, runtime cognition, language, both SDKs, interpretation/reasoning/planning/execution/AI and Gemini/OpenRouter/OpenAI/LiteRT providers.

## Physical checklist — not executed

- Authorized API 36/37 device: discover a cooperating external app, execute a scalar function and confirm visible effect independently.
- API 37: bind an activity-scoped function, change activity, reject old handles and rediscover.
- DirectAction app: compare precedence with an equivalent AppFunction; invalidate actions on app/activity changes.
- Accepted-then-timeout operation: retain UNKNOWN and prove no weaker-route duplicate effect.
- Current-app archive/mark unread: prefer semantic route; use fresh unambiguous accessibility only when permitted; no coordinates.
- Volume 50 then make it 20; open Spotify then pause it; expire context and verify clarification.
- Two contacts named John: choose explicitly, retain body in memory, open composer, never claim sent. Verify no SMS permissions.
- Internet/volume panels, protected authentication manual takeover and explicit Continue/Stop.
- Hide assistant overlay during a pending workflow: durable state remains; microphone and transient activity handles release.
- Compare CHAT provider choices and wearable invocation: identical native authority and route decisions.
- Inspect Activity and Skills truth; audit logs/journal for private text and verify bounded current-app tools.

Hardware, live provider and wearable outcomes must be recorded with device/API/app versions and independent effect evidence before changing this status.
