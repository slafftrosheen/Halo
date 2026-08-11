# Tools Directory

This directory contains auxiliary tools and scripts for the Halo project.

## heycyan-forensics/

### Purpose
BLE protocol forensics tool for systematic discovery of HeyCyan smart glasses communication protocols.

**Version 3 - Passive Mode (PR-017C)**: 90-second passive capture with delayed stream verification.

### Usage
```bash
cd tools/heycyan-forensics
pip install bleak
python heycyan_recorder.py

# Override capture duration (default: 90 seconds)
PASSIVE_SECONDS=120 python heycyan_recorder.py
```

### Operation Flow
```
SCAN (15s) → DISCOVER → CONNECT (MTU 517) → GATT DISCOVERY → 
SUBSCRIBE (all notify/indicate) → PASSIVE LISTEN (90s) → CAPTURE
```

### Output
Captures are saved as JSON files in `captures/` with:
- Full GATT service enumeration with characteristic properties and descriptors
- Full UUID for each notification characteristic
- Timestamped packet captures with elapsed time relative to first notification
- Subscription success/failure records
- Per-characteristic statistics

### Passive Mode
This recorder operates in STRICTLY PASSIVE mode:
- ✅ Connects and discovers services
- ✅ Subscribes to notifications  
- ✅ Records all received BLE packets
- ❌ Does NOT send any write commands
- ❌ Does NOT read arbitrary characteristics
- ❌ Does NOT speculate about device initialization

### Evidence-Based Timing
Based on Android Halo capture logs showing ~18-second delayed stream startup:
- Default capture: 90 seconds
- Expected first activity: may take >20 seconds after CCCD writes
- Continuous elapsed time display during capture

### Capture Output Format
```text
RX SUMMARY
────────────────────────────────────────

FIRST RX:
  elapsed:     18.631s
  characteristic: de5bf729-d711-4e47-af26-65e3012a5dc7
  length:        24B
  raw:           BC 59 28 00 ...

CHARACTERISTICS:
UUID                                              pkts    first     last    lengths
----------------------------------------------------------------------------------------------------
de5bf729...                                  1837  +0.018s +89.992s 24B, 44B, 28B, ...
0000ae02...                                     0
...

TOTAL PACKETS: 1837
```

### Files
- `heycyan_recorder.py` - Main recording script (v3 passive mode)
- `requirements.txt` - Python dependencies (bleak)
- `captures/` - JSON capture output directory

### Debug Checklist
If capture shows 0 packets:
1. Verify device is powered on and discoverable
2. Verify NO companion app is running on device
3. Check GATT shows correct characteristics subscribed
4. Wait full capture duration - streaming may be delayed

### Expected Behavior
- **Passive baseline**: Device may remain silent for ~18 seconds, then begin streaming
- **First RX**: Usually `de5bf729` characteristic with `BC 73` or `BC 59` header
- **BC 73**: Initial timestamped packet
- **BC 59**: Repeated sensor/frame data packets