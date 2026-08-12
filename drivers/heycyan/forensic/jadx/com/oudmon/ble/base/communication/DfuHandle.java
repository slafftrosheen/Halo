/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication;

import android.util.Log;
import com.oudmon.ble.base.bluetooth.BleOperateManager;
import com.oudmon.ble.base.bluetooth.OnGattEventCallback;
import com.oudmon.ble.base.communication.JPackageManager;
import com.oudmon.ble.base.communication.utils.CRC16;
import com.oudmon.ble.base.request.WriteRequest;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.UUID;

public class DfuHandle {
    public static final UUID SERIAL_PORT_SERVICE = UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7");
    public static final UUID SERIAL_PORT_CHAREACTER_NOTIRY = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7");
    public static final UUID SERIAL_PORT_CHAREACTER_WRITE = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7");
    private static final String TAG = "DfuHandle";
    public static final int RSP_OK = 0;
    public static final int RSP_DATA_SIZE = 1;
    public static final int RSP_DATA_CONTENT = 2;
    public static final int RSP_CMD_STATUS = 3;
    public static final int RSP_CMD_FORMAT = 4;
    public static final int RSP_INNER = 5;
    public static final int RSP_LOW_BATTERY = 6;
    private byte[] dfuData;
    private IOpResult iOpResult;
    private int mPackageLength;
    private static DfuHandle odmHandle;
    private OnGattEventCallback localEventCallback = new OnGattEventCallback(){

        @Override
        public void onReceivedData(String uuid, byte[] data) {
            if (data == null) {
                return;
            }
            if (DfuHandle.this.checkTheData(data)) {
                if (data[6] == 0 && data[1] == 3) {
                    if (!DfuHandle.this.readNextBigPocket()) {
                        DfuHandle.this.iOpResult.onProgress(100);
                    } else {
                        DfuHandle.this.iOpResult.onProgress(DfuHandle.this.bigPocketIndex * 1024 * 100 / DfuHandle.this.dfuData.length);
                        return;
                    }
                }
                if (DfuHandle.this.iOpResult != null && (data[0] & 0xFF) == 188 && data[1] <= 5) {
                    DfuHandle.this.iOpResult.onActionResult(data[1], data[6]);
                }
            }
        }
    };
    private short dfuFileChecksum = 0;
    private short dfuFileCrc16 = 0;
    private short bigPocketIndex = 0;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static DfuHandle getInstance() {
        if (odmHandle != null) return odmHandle;
        Class<DfuHandle> clazz = DfuHandle.class;
        synchronized (DfuHandle.class) {
            if (odmHandle != null) return odmHandle;
            odmHandle = new DfuHandle();
            // ** MonitorExit[var0] (shouldn't be in output)
            return odmHandle;
        }
    }

    public DfuHandle() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
    }

    public void initCallback() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
        BleOperateManager.getInstance().setCallback(this.localEventCallback);
    }

    public void setDeviceOperateManagerCallback() {
        BleOperateManager.getInstance().setCallback(this.localEventCallback);
    }

    private void openNotify() {
    }

    private boolean checkTheData(byte[] data) {
        Log.i((String)TAG, (String)("checkTheData: data=" + DataTransferUtils.getHexString(data)));
        if (data.length >= 6) {
            if ((data[0] & 0xFF) == 188 && DataTransferUtils.bytesToShort(data, 2) == data.length - 6) {
                int crc = CRC16.calcCrc16(Arrays.copyOfRange(data, 6, data.length));
                if ((DataTransferUtils.bytesToShort(data, 4) & 0xFFFF) == crc) {
                    return true;
                }
                Log.e((String)TAG, (String)"checkTheData: CRC \u6821\u9a8c\u5931\u8d25");
            } else {
                Log.e((String)TAG, (String)"checkTheData: \u6570\u636e\u957f\u5ea6\u4e0d\u4e00\u81f4");
            }
        } else {
            Log.e((String)TAG, (String)"checkTheData: \u534f\u8bae\u957f\u5ea6\u6709\u95ee\u9898");
        }
        return false;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean checkFile(String filePath) {
        Log.i((String)TAG, (String)("OTA\u5347\u7ea7\u8c03\u8bd5--\u9009\u62e9\u5347\u7ea7\u6587\u4ef6\uff1a" + filePath));
        File dfuNrfFile = new File(filePath);
        if (!dfuNrfFile.exists()) {
            Log.i((String)TAG, (String)"OTA\u5347\u7ea7\u8c03\u8bd5--\u6587\u4ef6\u4e0d\u5b58\u5728\uff01");
            return false;
        }
        RandomAccessFile randomAccessFile = null;
        try {
            this.dfuFileChecksum = 0;
            this.dfuFileCrc16 = 0;
            randomAccessFile = new RandomAccessFile(filePath, "r");
            if (randomAccessFile.length() > 0xBB8000L) {
                Log.i((String)TAG, (String)"\u6587\u4ef6\u5927\u5c0f\u6ea2\u51fa\uff01");
                boolean bl = false;
                return bl;
            }
            this.dfuData = new byte[(int)randomAccessFile.length()];
            int size = randomAccessFile.read(this.dfuData, 0, this.dfuData.length);
            Log.i((String)TAG, (String)("start cal check sum.. dataSize=" + this.dfuData.length + "  readSize=" + size));
            this.dfuFileChecksum = 0;
            for (int i = 0; i < this.dfuData.length; ++i) {
                this.dfuFileChecksum = (short)(this.dfuFileChecksum + (this.dfuData[i] & 0xFF));
            }
            this.dfuFileChecksum = (short)(this.dfuFileChecksum & 0xFFFFFFFF);
            this.dfuFileCrc16 = (short)CRC16.calcCrc16(this.dfuData);
            Log.i((String)TAG, (String)("dfuFileChecksum: " + this.dfuFileChecksum + ", dfuFileCrc16: " + this.dfuFileCrc16));
            boolean bl = true;
            return bl;
        }
        catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            if (randomAccessFile != null) {
                try {
                    randomAccessFile.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    public void start(IOpResult iOpResult) {
        this.openNotify();
        this.iOpResult = iOpResult;
        this.setDeviceOperateManagerCallback();
        BleOperateManager.getInstance().execute(this.getWriteRequest(this.addHeader(1, null)));
    }

    public void init() {
        this.setDeviceOperateManagerCallback();
        byte[] data = new byte[9];
        data[0] = 1;
        System.arraycopy(DataTransferUtils.intToBytes(this.dfuData.length), 0, data, 1, 4);
        System.arraycopy(DataTransferUtils.shortToBytes(this.dfuFileCrc16), 0, data, 5, 2);
        System.arraycopy(DataTransferUtils.shortToBytes(this.dfuFileChecksum), 0, data, 7, 2);
        BleOperateManager.getInstance().execute(this.getWriteRequest(this.addHeader(2, data)));
    }

    public void sendPacket() {
        this.bigPocketIndex = 0;
        this.readNextBigPocket();
    }

    private boolean readNextBigPocket() {
        this.setDeviceOperateManagerCallback();
        if (this.bigPocketIndex * 1024 >= this.dfuData.length) {
            Log.i((String)TAG, (String)"\u5347\u7ea7\u5305\u53d1\u9001\u5b8c\u6bd5");
            return false;
        }
        byte[] bufferNext = new byte[Math.min(1024, this.dfuData.length - this.bigPocketIndex * 1024) + 2];
        System.arraycopy(DataTransferUtils.shortToBytes((short)(this.bigPocketIndex + 1)), 0, bufferNext, 0, 2);
        System.arraycopy(this.dfuData, this.bigPocketIndex * 1024, bufferNext, 2, bufferNext.length - 2);
        this.sendPocketToBle(this.addHeader(3, bufferNext));
        this.bigPocketIndex = (short)(this.bigPocketIndex + 1);
        return true;
    }

    private void sendPocketToBle(byte[] bigPocket) {
        Log.i((String)TAG, (String)("sendPocketToBle: bigPocket=" + DataTransferUtils.getHexString(bigPocket)));
        this.setDeviceOperateManagerCallback();
        int index = 0;
        while (index * this.mPackageLength < bigPocket.length) {
            BleOperateManager.getInstance().execute(this.getWriteRequest(Arrays.copyOfRange(bigPocket, index * this.mPackageLength, index * this.mPackageLength + Math.min(this.mPackageLength, bigPocket.length - index * this.mPackageLength))));
            ++index;
        }
    }

    public void check() {
        this.setDeviceOperateManagerCallback();
        BleOperateManager.getInstance().execute(this.getWriteRequest(this.addHeader(4, null)));
    }

    public void endAndRelease() {
        this.iOpResult = null;
        BleOperateManager.getInstance().execute(this.getWriteRequest(this.addHeader(5, null)));
        BleOperateManager.getInstance().setCallback(null);
    }

    private byte[] addHeader(int cmdid, byte[] data) {
        byte[] pocket = new byte[(data == null ? 0 : data.length) + 6];
        pocket[0] = -68;
        pocket[1] = (byte)cmdid;
        if (data != null && data.length > 0) {
            System.arraycopy(DataTransferUtils.shortToBytes((short)data.length), 0, pocket, 2, 2);
            System.arraycopy(DataTransferUtils.shortToBytes((short)CRC16.calcCrc16(data)), 0, pocket, 4, 2);
            System.arraycopy(data, 0, pocket, 6, data.length);
        } else {
            pocket[4] = -1;
            pocket[5] = -1;
        }
        return pocket;
    }

    private WriteRequest getWriteRequest(byte[] data) {
        WriteRequest noRspInstance = WriteRequest.getNoRspInstance(SERIAL_PORT_SERVICE, SERIAL_PORT_CHAREACTER_WRITE);
        noRspInstance.setValue(data);
        return noRspInstance;
    }

    public static interface IOpResult {
        public void onActionResult(int var1, int var2);

        public void onProgress(int var1);
    }
}

