# Tools Directory

This directory contains auxiliary tools and scripts for the Halo project.

## heycyan-forensics/

### Purpose
BLE protocol forensics tool for systematic discovery of HeyCyan smart glasses communication protocols.

### Usage
```bash
cd tools/heycyan-forensics
pip install bleak
python heycyan_recorder.py
```

### Output
Captures are saved as JSON files in `captures/` with:
- Full GATT service enumeration
- Timestamped packet captures
- Action correlation data

### Interaction Matrix
The recorder tests 13 predefined interactions:
- AI button: single, double, long press
- Camera button: single, double press
- Touch panel: swipe left/right, tap, double tap, hold
- Wear sensor: remove glasses, put glasses on
- Baseline: 5 second idle period

### Files
- `heycyan_recorder.py` - Main recording script
- `requirements.txt` - Python dependencies (bleak)
- `captures/` - JSON capture output directory