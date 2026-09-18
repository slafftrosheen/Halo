# HeyCyan BLE implementation entry point

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

Use [implementation status](IMPLEMENTATION_STATUS.md), the driver source and its actual
execution mode. The previous synthetic-only/no-imported-captures description is obsolete:
RealHeyCyanDriver has response routing and capture handling, and research/evidence contains
capture artifacts.

Discovery and runtime sessions must remain distinguished. A raw discovery session should
not promote decoded meanings or guessed commands; a runtime session may use implemented
decoder/command paths. Verify the caller/mode rather than applying the old “all unknown”
statement to every session.

Protocol references are research, not current-build hardware certificates. Preserve unknown
traffic/provenance and validate outbound behavior against the selected transport and firmware.
