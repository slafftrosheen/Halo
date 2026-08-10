# HeyCyan Driver — Verification Checklist (PR-030)

> Run this checklist locally to confirm the HeyCyan driver stack meets the
> criteria defined in `working_criteria.md`.

---

## Prerequisites

- [ ] JDK 17+ on PATH (`java -version`)
- [ ] Android SDK installed (not required for JVM-only tests, but needed for the full module build)
- [ ] No physical device required for any check in this list

---

## Step 1 — Run Unit Tests

```bash
./gradlew :drivers:heycyan:test
```

**Expected:** `BUILD SUCCESSFUL` with 0 test failures.

Check the report at:
```
drivers/heycyan/build/reports/tests/test/index.html
```

---

## Step 2 — Verify Capability Catalog

Confirm the `HeyCyanCapabilityValidatorTest` passes:

```bash
./gradlew :drivers:heycyan:test --tests "com.halo.drivers.heycyan.capability.HeyCyanCapabilityValidatorTest"
```

**Expected:** All 3 CAT-xx tests pass.

---

## Step 3 — Verify Transport Routing

Confirm the `HeyCyanTransportRoutingTest` passes:

```bash
./gradlew :drivers:heycyan:test --tests "com.halo.drivers.heycyan.capability.HeyCyanTransportRoutingTest"
```

**Expected:** All 7 CR-xx tests pass.

---

## Step 4 — Verify Media Synchronization

Confirm the `HeyCyanMediaSynchronizerTest` passes:

```bash
./gradlew :drivers:heycyan:test --tests "com.halo.drivers.heycyan.media.HeyCyanMediaSynchronizerTest"
```

**Expected:** All 6 MS-xx tests pass.

---

## Step 5 — Verify No Android SDK Leakage

Grep for Android SDK imports in the `src/main/` driver layer (excluding the Android host files):

```bash
grep -r "import android\." \
  drivers/heycyan/src/main/java/com/halo/drivers/heycyan/capability \
  drivers/heycyan/src/main/java/com/halo/drivers/heycyan/transport \
  drivers/heycyan/src/main/java/com/halo/drivers/heycyan/media \
  drivers/heycyan/src/main/java/com/halo/drivers/heycyan/wifi
```

**Expected:** Zero results.

---

## Step 6 — Verify No Runtime References

Grep for runtime/engine imports in the driver layer:

```bash
grep -r "import com.halo.runtime\|import com.halo.cognitive\|import com.halo.engine\|Blackboard\|Timeline\|RuntimeSnapshot" \
  drivers/heycyan/src/main/java/com/halo/drivers/heycyan
```

**Expected:** Zero results.

---

## Step 7 — Review Blocked Items

Review the following known-incomplete items are still accurately catalogued
in `HeyCyanCapabilityCatalog`:

| Capability | Expected Status |
|-----------|----------------|
| `device.datetime` | `BLOCKED_BY_UNKNOWN_PROTOCOL` |
| `camera.ai_photo` | `BLOCKED_BY_UNKNOWN_PROTOCOL` |
| `bluetooth.rfcomm` | `IMPLEMENTED_WITH_LIMITATION` |
| `media.download` | `IMPLEMENTED_WITH_LIMITATION` |

**Expected:** Each descriptor matches the table above.

---

## Pass / Fail Summary

| Check | Status |
|-------|--------|
| Unit tests pass | ⬜ |
| Capability validator passes | ⬜ |
| Transport routing tests pass | ⬜ |
| Media synchronization tests pass | ⬜ |
| No Android SDK leakage in core layer | ⬜ |
| No Runtime references in driver | ⬜ |
| Blocked items correctly catalogued | ⬜ |

All items must be ✅ before merging PR-030.
