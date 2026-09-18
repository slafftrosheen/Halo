# Protocol laboratory

Status: CURRENT SOURCE MAP / PLANNED. Reviewed 2026-09-18.

The lab capture/replay/analysis/protocol/diagnostics modules are Gradle scaffolds.
Their intended reusable capture tooling still primarily resides in drivers/heycyan.
Do not infer migrated functionality from the module names.

The reviewed capture/replay build files use JVM plugins; capture explicitly avoids a
driver dependency until migration. The former “temporarily depends on drivers:heycyan”
statement did not match that build file. Future migration should follow a demonstrated
reuse need and preserve raw evidence.
