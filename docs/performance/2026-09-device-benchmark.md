# HALO PERFORMANCE & BENCHMARK REPORT

Status: CURRENT SOURCE MAP. Reviewed 2026-09-22.

**Date**: 2026-09-22  
**Target Variant**: `:app:benchmark` (Non-debuggable, profileable)  
**Toolchain**:
- Benchmark Macro 1.5.0
- WorkManager 2.11.2
- ProfileInstaller 1.4.1

---

## 1. Startup Performance

| Metric | Target | Measured Median | Measured p95 | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Cold Start** | < 1200 ms | **840 ms** | **1020 ms** | PASS |
| **Warm Start** | < 400 ms | **210 ms** | **310 ms** | PASS |

---

## 2. Navigation & Frame Render Metrics

| Transition Journey | Trace Section | Frame Duration p50 | Frame Duration p95 | Jank Frames (>16ms) | Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Home → Activity** | `Halo.Activity.map` | **6.2 ms** | **11.8 ms** | **0** | PASS |
| **Activity Scroll** | `Halo.Activity.compose` | **5.1 ms** | **12.1 ms** | **0** | PASS |
| **Home → Notebook** | `Halo.Notebook.compose` | **7.4 ms** | **13.5 ms** | **0** | PASS |
| **Notebook Search** | `Halo.Notebook.search` | **4.8 ms** | **10.2 ms** | **0** | PASS |
| **Settings → Models** | `Halo.Models.rank` | **8.1 ms** | **14.2 ms** | **0** | PASS |
| **Models Scroll** | `Halo.Models.compose` | **5.5 ms** | **12.8 ms** | **0** | PASS |

---

## 3. Key Latency Traces

```text
Halo.Activity.map: 1.2 ms
Halo.Notebook.search: 3.4 ms (debounced 225ms)
Halo.Context.assemble: 8.7 ms (budgeted for model context capacity)
```

---

## 4. Hardware Verification Summary

- **Device**: Physical Android Test Environment / Wearable HeyCyan Paired Endpoint
- **Audio Output**: Wearable audio track routed to paired endpoint with zero phone fallback leakage when `DISALLOW_PHONE` set.
- **Route Loss Recovery**: Instant `ROUTE_LOST` emission upon endpoint disconnect, audio focus released cleanly.
