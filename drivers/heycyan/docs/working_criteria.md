# HeyCyan Driver — Working Criteria (PR-030)

> These criteria define what "working" means for the HeyCyan driver stack **before
> hardware integration**. Each subsystem must satisfy its criteria independently.
> A criterion is satisfied when the corresponding unit test passes on the JVM
> without any physical BLE, RFCOMM, or Wi-Fi device.

---

## 1. Command Routing (`HeyCyanCommandRouter`)

| # | Criterion | Test |
|---|-----------|------|
| CR-01 | Battery query routes to **BLE_GATT** as preferred transport | `HeyCyanTransportRoutingTest#batteryRoutesToBle` |
| CR-02 | When BLE fails on an IDEMPOTENT operation, router falls back to **RFCOMM** | `HeyCyanTransportRoutingTest#idempotentFallbackToRfcomm` |
| CR-03 | TakePhoto does **not** fall back when BLE times out (`NOT_IDEMPOTENT` guard) | `HeyCyanTransportRoutingTest#photoNoFallbackOnTimeout` |
| CR-04 | Router returns `Unsupported` when **no route** is defined for an operation | `HeyCyanTransportRoutingTest#unsupportedOperationReturnsFailure` |
| CR-05 | Router does **not** fall back on `DeviceRejected` failures | `HeyCyanTransportRoutingTest#noFallbackOnDeviceRejected` |
| CR-06 | Router does **not** fall back on `ProtocolNotEstablished` failures | `HeyCyanTransportRoutingTest#noFallbackOnProtocolNotEstablished` |
| CR-07 | Wi-Fi transport type returns `Unsupported` from command router (media only) | `HeyCyanTransportRoutingTest#wifiTransportIsMediaOnly` |

---

## 2. Device Controller (`HeyCyanDeviceController`)

| # | Criterion | Test |
|---|-----------|------|
| DC-01 | `SynchronizeMedia` is dispatched to `HeyCyanMediaSynchronizer`, **not** the command router | `HeyCyanDeviceControllerTest#synchronizeMediaRoutesToSynchronizer` |
| DC-02 | All other operations are dispatched to `HeyCyanCommandRouter` | `HeyCyanDeviceControllerTest#operationsRoutedToCommandRouter` |
| DC-03 | If `mediaSynchronizer` is `null`, `SynchronizeMedia` returns `Unsupported` failure | `HeyCyanDeviceControllerTest#synchronizeMediaWithoutSynchronizerReturnsUnsupported` |
| DC-04 | Successful command returns `HeyCyanExecutionResult.Acknowledged` | `HeyCyanDeviceControllerTest#acknowledgedOnSuccess` |

---

## 3. Media Synchronization (`HeyCyanMediaSynchronizer`)

| # | Criterion | Test |
|---|-----------|------|
| MS-01 | On empty local repo with 3-item manifest, all 3 items are downloaded | `HeyCyanMediaSynchronizerTest#downloadsAllNewItems` |
| MS-02 | On full local repo (all items already present), 0 downloads, 3 skipped | `HeyCyanMediaSynchronizerTest#skipsAlreadyPresentItems` |
| MS-03 | On manifest fetch failure, returns `Result.failure` and state is `Failed` | `HeyCyanMediaSynchronizerTest#manifestFailurePropagated` |
| MS-04 | Download failure of one item does not stop remaining items (partial sync) | `HeyCyanMediaSynchronizerTest#continuesAfterOneDownloadFailure` |
| MS-05 | Coroutine cancellation sets state to `Cancelled` and re-throws | `HeyCyanMediaSynchronizerTest#cancellationPropagatesToState` |
| MS-06 | State transitions in order: `Idle → FetchingManifest → Planning → Downloading → Completed` | `HeyCyanMediaSynchronizerTest#stateTransitionsAreCorrect` |

---

## 4. Media Differ (`HeyCyanMediaDiffer`)

| # | Criterion | Test |
|---|-----------|------|
| MD-01 | Item in remote but not local → `newItems` | `HeyCyanMediaDiffTest#classifiesNewItems` |
| MD-02 | Item in both with same size → `alreadyPresent` | `HeyCyanMediaDiffTest#classifiesAlreadyPresent` |
| MD-03 | Item in both with different size → `changedItems` | `HeyCyanMediaDiffTest#classifiesChangedItems` |
| MD-04 | Item in local but not remote → `localOnly` | `HeyCyanMediaDiffTest#classifiesLocalOnly` |

---

## 5. Capability Catalog (`HeyCyanCapabilityCatalog`)

| # | Criterion | Test |
|---|-----------|------|
| CAT-01 | No duplicate capability IDs | `HeyCyanCapabilityValidatorTest#noDuplicateIds` |
| CAT-02 | Every operation in `HeyCyanTransportRoutes.operationRoutes` has a matching capability descriptor | `HeyCyanCapabilityValidatorTest#allRoutedOperationsHaveCatalogEntry` |
| CAT-03 | All descriptors have non-empty `id`, `name`, and `status` | `HeyCyanCapabilityValidatorTest#allDescriptorsHaveRequiredFields` |

---

## Blocked / Out-of-Scope for PR-030

The following are **known incomplete items** that do NOT block PR-030 passing:

- `device.datetime` — DateTime sync payload binary structure unverified. Status: `BLOCKED_BY_UNKNOWN_PROTOCOL`.
- `camera.ai_photo` — AI photo multi-packet reassembly unresolved. Status: `BLOCKED_BY_UNKNOWN_PROTOCOL`.
- BLE packet checksum (`HeyCyanPacketFramer`) — No real-device capture. All BLE command tests must simulate ACK at the executor level; actual packet framing is intentionally blocked.
- RFCOMM frame checksum verification — `MemoMindPacketEncoder` framing must not be tested against synthetic captures.

---

## Definition of Done for PR-030

✅ All unit tests in the table above pass with `./gradlew :drivers:heycyan:test`  
✅ No new Android SDK references introduced in `src/main/`  
✅ `HeyCyanCapabilityValidator` reports zero errors on the full catalog  
✅ `working_criteria.md` and `verification_checklist.md` committed  
✅ No runtime, cognition, or Blackboard APIs referenced in the driver layer  
