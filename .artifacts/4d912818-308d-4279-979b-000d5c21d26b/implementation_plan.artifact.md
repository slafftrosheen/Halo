# Plan - Fix HeyCyan Connection (RFCOMM Fallback)

The log shows that the RFCOMM connection to the 'primary' channel failed. Since `PRIMARY` is currently marked as `required = true`, the driver reports a connection failure even though it should attempt to proceed with BLE (GATT) if available.

## Proposed Changes

### Driver & Protocol

#### [MODIFY] [HeyCyanRfcommChannel.kt](file:///C:/Users/Slaff/Halo/drivers/heycyan/src/main/java/com/halo/drivers/heycyan/rfcomm/HeyCyanRfcommChannel.kt)
- Change `PRIMARY` channel's `required` property to `false`.
- This ensures that a failure to connect to the standard Serial Port Profile (SPP) UUID does not block the overall connection flow. The driver already checks for at least one usable transport (GATT or RFCOMM) at the end of the `connect()` method.

## Verification Plan

### Manual Verification
1. Launch app -> Protocol Lab.
2. Tap **Discover**.
3. Tap **Connect** on your HeyCyan glasses.
4. Verify in Logcat that while RFCOMM might still fail (if the glasses are not in pairing mode or already occupied), the driver now proceeds to the "Starting GATT connection..." step and eventually reaches "Connected" status via BLE.
