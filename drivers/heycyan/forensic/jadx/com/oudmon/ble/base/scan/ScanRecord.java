/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.os.ParcelUuid
 *  android.util.ArrayMap
 *  android.util.SparseArray
 *  androidx.annotation.Nullable
 *  androidx.annotation.RequiresApi
 */
package com.oudmon.ble.base.scan;

import android.os.ParcelUuid;
import android.util.ArrayMap;
import android.util.SparseArray;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RequiresApi(api=21)
public class ScanRecord {
    private static final String TAG = "ScanRecord";
    private static final int DATA_TYPE_FLAGS = 1;
    private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_PARTIAL = 2;
    private static final int DATA_TYPE_SERVICE_UUIDS_16_BIT_COMPLETE = 3;
    private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_PARTIAL = 4;
    private static final int DATA_TYPE_SERVICE_UUIDS_32_BIT_COMPLETE = 5;
    private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_PARTIAL = 6;
    private static final int DATA_TYPE_SERVICE_UUIDS_128_BIT_COMPLETE = 7;
    private static final int DATA_TYPE_LOCAL_NAME_SHORT = 8;
    private static final int DATA_TYPE_LOCAL_NAME_COMPLETE = 9;
    private static final int DATA_TYPE_TX_POWER_LEVEL = 10;
    private static final int DATA_TYPE_SERVICE_DATA = 22;
    private static final int DATA_TYPE_MANUFACTURER_SPECIFIC_DATA = 255;
    private final int mAdvertiseFlags;
    @Nullable
    private final List<ParcelUuid> mServiceUuids;
    private final SparseArray<byte[]> mManufacturerSpecificData;
    private final Map<ParcelUuid, byte[]> mServiceData;
    private final int mTxPowerLevel;
    private final String mDeviceName;
    private final byte[] mBytes;
    static final char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static final ParcelUuid BASE_UUID = ParcelUuid.fromString((String)"00000000-0000-1000-8000-00805F9B34FB");

    static String toString(SparseArray<byte[]> array) {
        if (array == null) {
            return "null";
        }
        if (array.size() == 0) {
            return "{}";
        }
        StringBuilder buffer = new StringBuilder();
        buffer.append('{');
        for (int i = 0; i < array.size(); ++i) {
            buffer.append(array.keyAt(i)).append("=").append(Arrays.toString((byte[])array.valueAt(i)));
        }
        buffer.append('}');
        return buffer.toString();
    }

    static <T> String toString(Map<T, byte[]> map) {
        if (map == null) {
            return "null";
        }
        if (map.isEmpty()) {
            return "{}";
        }
        StringBuilder buffer = new StringBuilder();
        buffer.append('{');
        Iterator<Map.Entry<T, byte[]>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<T, byte[]> entry = it.next();
            T key = entry.getKey();
            buffer.append(key).append("=").append(Arrays.toString(map.get(key)));
            if (!it.hasNext()) continue;
            buffer.append(", ");
        }
        buffer.append('}');
        return buffer.toString();
    }

    public int getAdvertiseFlags() {
        return this.mAdvertiseFlags;
    }

    public List<ParcelUuid> getServiceUuids() {
        return this.mServiceUuids;
    }

    public SparseArray<byte[]> getManufacturerSpecificData() {
        return this.mManufacturerSpecificData;
    }

    @Nullable
    public byte[] getManufacturerSpecificData(int manufacturerId) {
        return (byte[])this.mManufacturerSpecificData.get(manufacturerId);
    }

    public Map<ParcelUuid, byte[]> getServiceData() {
        return this.mServiceData;
    }

    @Nullable
    public byte[] getServiceData(ParcelUuid serviceDataUuid) {
        if (serviceDataUuid == null) {
            return null;
        }
        return this.mServiceData.get(serviceDataUuid);
    }

    public int getTxPowerLevel() {
        return this.mTxPowerLevel;
    }

    @Nullable
    public String getDeviceName() {
        return this.mDeviceName;
    }

    public byte[] getBytes() {
        return this.mBytes;
    }

    private ScanRecord(List<ParcelUuid> serviceUuids, SparseArray<byte[]> manufacturerData, Map<ParcelUuid, byte[]> serviceData, int advertiseFlags, int txPowerLevel, String localName, byte[] bytes) {
        this.mServiceUuids = serviceUuids;
        this.mManufacturerSpecificData = manufacturerData;
        this.mServiceData = serviceData;
        this.mDeviceName = localName;
        this.mAdvertiseFlags = advertiseFlags;
        this.mTxPowerLevel = txPowerLevel;
        this.mBytes = bytes;
    }

    public static ScanRecord parseFromBytes(byte[] scanRecord) {
        if (scanRecord == null) {
            return null;
        }
        int advertiseFlag = -1;
        ArrayList<ParcelUuid> serviceUuids = new ArrayList<ParcelUuid>();
        String localName = null;
        int txPowerLevel = Integer.MIN_VALUE;
        SparseArray manufacturerData = new SparseArray();
        ArrayMap serviceData = new ArrayMap();
        try {
            int length;
            int dataLength;
            block12: for (int currentPos = 0; currentPos < scanRecord.length && (length = scanRecord[currentPos++] & 0xFF) != 0; currentPos += dataLength) {
                dataLength = length - 1;
                int fieldType = scanRecord[currentPos++] & 0xFF;
                switch (fieldType) {
                    case 1: {
                        advertiseFlag = scanRecord[currentPos] & 0xFF;
                        continue block12;
                    }
                    case 2: 
                    case 3: {
                        ScanRecord.parseServiceUuid(scanRecord, currentPos, dataLength, 16, serviceUuids);
                        continue block12;
                    }
                    case 4: 
                    case 5: {
                        ScanRecord.parseServiceUuid(scanRecord, currentPos, dataLength, 32, serviceUuids);
                        continue block12;
                    }
                    case 6: 
                    case 7: {
                        ScanRecord.parseServiceUuid(scanRecord, currentPos, dataLength, 128, serviceUuids);
                        continue block12;
                    }
                    case 8: 
                    case 9: {
                        localName = new String(ScanRecord.extractBytes(scanRecord, currentPos, dataLength));
                        continue block12;
                    }
                    case 10: {
                        txPowerLevel = scanRecord[currentPos];
                        continue block12;
                    }
                    case 22: {
                        int serviceUuidLength = 16;
                        byte[] serviceDataUuidBytes = ScanRecord.extractBytes(scanRecord, currentPos, serviceUuidLength);
                        ParcelUuid serviceDataUuid = ScanRecord.parseUuidFrom(serviceDataUuidBytes);
                        byte[] serviceDataArray = ScanRecord.extractBytes(scanRecord, currentPos + serviceUuidLength, dataLength - serviceUuidLength);
                        serviceData.put(serviceDataUuid, serviceDataArray);
                        continue block12;
                    }
                    case 255: {
                        int manufacturerId = ((scanRecord[currentPos + 1] & 0xFF) << 8) + (scanRecord[currentPos] & 0xFF);
                        byte[] manufacturerDataBytes = ScanRecord.extractBytes(scanRecord, currentPos + 2, dataLength - 2);
                        manufacturerData.put(manufacturerId, (Object)manufacturerDataBytes);
                        continue block12;
                    }
                }
            }
            if (serviceUuids.isEmpty()) {
                serviceUuids = null;
            }
            return new ScanRecord(serviceUuids, (SparseArray<byte[]>)manufacturerData, (Map<ParcelUuid, byte[]>)serviceData, advertiseFlag, txPowerLevel, localName, scanRecord);
        }
        catch (Exception e) {
            return new ScanRecord(null, (SparseArray<byte[]>)manufacturerData, null, -1, Integer.MIN_VALUE, null, scanRecord);
        }
    }

    public String toString() {
        return "ScanRecord [mAdvertiseFlags=" + this.mAdvertiseFlags + ", mServiceUuids=" + this.mServiceUuids + ", mManufacturerSpecificData=" + ScanRecord.toString(this.mManufacturerSpecificData) + ", mServiceData=" + ScanRecord.toString(this.mServiceData) + ", mTxPowerLevel=" + this.mTxPowerLevel + ", mDeviceName=" + this.mDeviceName + "]";
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; ++j) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0xF];
        }
        return new String(hexChars);
    }

    private static int parseServiceUuid(byte[] scanRecord, int currentPos, int dataLength, int uuidLength, List<ParcelUuid> serviceUuids) {
        while (dataLength > 0) {
            byte[] uuidBytes = ScanRecord.extractBytes(scanRecord, currentPos, uuidLength);
            serviceUuids.add(ScanRecord.parseUuidFrom(uuidBytes));
            dataLength -= uuidLength;
            currentPos += uuidLength;
        }
        return currentPos;
    }

    private static byte[] extractBytes(byte[] scanRecord, int start, int length) {
        byte[] bytes = new byte[length];
        System.arraycopy(scanRecord, start, bytes, 0, length);
        return bytes;
    }

    public static ParcelUuid parseUuidFrom(byte[] uuidBytes) {
        long shortUuid;
        if (uuidBytes == null) {
            throw new IllegalArgumentException("uuidBytes cannot be null");
        }
        int length = uuidBytes.length;
        if (length != 16 && length != 32 && length != 128) {
            throw new IllegalArgumentException("uuidBytes length invalid - " + length);
        }
        if (length == 128) {
            ByteBuffer buf = ByteBuffer.wrap(uuidBytes).order(ByteOrder.LITTLE_ENDIAN);
            long msb = buf.getLong(8);
            long lsb = buf.getLong(0);
            return new ParcelUuid(new UUID(msb, lsb));
        }
        if (length == 16) {
            shortUuid = uuidBytes[0] & 0xFF;
            shortUuid += (long)((uuidBytes[1] & 0xFF) << 8);
        } else {
            shortUuid = uuidBytes[0] & 0xFF;
            shortUuid += (long)((uuidBytes[1] & 0xFF) << 8);
            shortUuid += (long)((uuidBytes[2] & 0xFF) << 16);
            shortUuid += (long)((uuidBytes[3] & 0xFF) << 24);
        }
        long msb = BASE_UUID.getUuid().getMostSignificantBits() + (shortUuid << 32);
        long lsb = BASE_UUID.getUuid().getLeastSignificantBits();
        return new ParcelUuid(new UUID(msb, lsb));
    }
}

