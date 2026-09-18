# Security and privacy assessment

Status: CURRENT SOURCE MAP / OPEN REQUIREMENTS. Reviewed 2026-09-18.
This is source inspection, not a penetration test or security certification.

| Area | Current evidence | Limit |
|---|---|---|
| Keys | IntelligenceViewModel.setOpenRouterKey -> AndroidStorage SharedPreferences | No application-level encryption on this path; masked text is presentation only |
| Backup | Manifest allowBackup=true | Backup/restore exclusions need review |
| Context | Session/context assembly applies sharing policy | Provider adapters own clients; no universal unbypassable outbound gateway |
| Plugins | In-process registry/dispatch | No security sandbox established |
| Tools | Registration/argument validation | Does not establish user intent, Android permission or physical safety |
| Prompt injection | Typed context/tool boundaries | Sanitization does not prove resistance to hostile instructions |
| BLE | Transport/parser/error-handling code | Does not establish secure pairing for all transports/firmware |
| Diagnostics | Export implementation | “Zero secret leakage” requires actual log/export tests |

Sources: [storage](../../host-android/src/main/java/com/halo/host/android/AndroidStorage.kt),
[key writes](../../app/src/main/java/com/halo/app/ui/screens/settings/IntelligenceViewModel.kt),
[manifest](../../app/src/main/AndroidManifest.xml),
[authorizer](../../runtime/cognition/src/main/java/com/halo/runtime/cognition/tools/InMemoryToolAuthorizer.kt),
[plugins](../09-plugins/SPEC.md).

Required validation: hostile tool inputs, permission revocation, untrusted notifications/web
content, duplicate side effects, backup/restore, credential migration and redacted exports.
Do not mark a threat “handled” merely because a named module or design paragraph exists.
