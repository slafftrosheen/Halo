# Tools

Status: CURRENT SOURCE MAP. Reviewed 2026-09-18.

## Documentation checks

Run `python tools/check_docs.py` from the repository root to check status classification
and local Markdown targets. This checks documentation structure, not factual correctness.

## HeyCyan recorder

[heycyan_recorder.py](heycyan-forensics/heycyan_recorder.py) uses Bleak, scans for 15 seconds,
selects a device, enumerates GATT, subscribes and records a passive session.
Default duration is 90 seconds; PASSIVE_SECONDS overrides it.
It reads the negotiated client MTU; the old claim that it requests MTU 517 was incorrect.
Notification subscription can write CCCDs; “passive” means no application protocol commands,
not literally zero Bluetooth writes.

Install dependencies from the recorder's requirements file and run it manually with
authorized hardware. Output lives in tools/heycyan-forensics/captures. No capture was run
in this docs audit. Earlier example packet counts/delays were illustrative/historical,
not expected behavior for every device.
