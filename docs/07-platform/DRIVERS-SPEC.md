# Driver contracts

Status: CURRENT SOURCE MAP / DESIGN TARGET. Reviewed 2026-09-18.

Read [driver SDK](../../sdk/driver-sdk), [host SDK](../../sdk/host-sdk),
[HeyCyan](../../drivers/heycyan) and [simulator](../../drivers/simulator).
Device/session contracts also exist in sdk/runtime-sdk.

Discovery, connection, transport, capabilities and diagnostics are intended responsibilities.
The previous universal lifecycle diagram was not the exact enum of every driver.

Coroutine scopes and exception handling are not process isolation. There is no established
guarantee that SDK/native crashes cannot crash Halo or every operation has a timeout.
Use [HeyCyan status](../protocols/heycyan/IMPLEMENTATION_STATUS.md) for current limits.
