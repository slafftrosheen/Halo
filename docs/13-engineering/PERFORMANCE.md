# Performance targets

Status: DESIGN TARGET. Updated 2026-09-18.

These are proposals, not measurements or configured CI failures.
Current CI does not enforce physical battery/startup/frame budgets.

Earlier targets: memory RAM <40MB, CPU <1%, search <50ms; wake CPU <2%, latency <120ms;
provider cold start <1s. The PRD separately specifies wake <150ms, local answer <1s and
background drain <1.5%/hour. Resolve timing boundaries/conflicting targets before adoption.

Record hardware/OS/model/power/workload/warm-cold state/sample count and timing definitions.
Measure native model loading separately from lightweight local command execution.
