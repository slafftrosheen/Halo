/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.dfu_temperature;

import android.util.Log;
import com.oudmon.ble.base.bluetooth.IBleListener;
import com.oudmon.ble.base.communication.CompressUtils;
import com.oudmon.ble.base.communication.JPackageManager;
import com.oudmon.ble.base.communication.dfu_temperature.TemperatureEntity;
import com.oudmon.ble.base.communication.utils.CRC16;
import com.oudmon.ble.base.request.WriteRequest;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class TemperatureHandle {
    private static final String TAG = "TemperatureHandle";
    private static final byte ACTION_SERIES = 37;
    private static final byte ACTION_ONCE = 38;
    private static final UUID SERIAL_PORT_SERVICE = UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_NOTIFY = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_WRITE = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7");
    private IBleListener mBleManager;
    private HandlerCallback mCallback;
    private byte[] mReceivedData;
    private byte[] mFileSend;
    private short mPocketIndex = 0;
    private boolean mReceiving;
    private int mTotalCount = 0;
    private int mReceivedCount = 0;
    private int mPackageLength;
    private List<String> mFileNames = new ArrayList<String>();
    private TemperatureEntity mTempEntity = new TemperatureEntity();

    public TemperatureHandle(IBleListener iBleManagerSrv) {
        this.mBleManager = iBleManagerSrv;
        this.mPackageLength = JPackageManager.getInstance().getLength();
    }

    private void parseReceivedData(byte[] data) {
        try {
            Log.i((String)TAG, (String)"===========================ParseReceivedData\u5f00\u59cb============================");
            this.mTempEntity.clear();
            this.mTempEntity.mIndex = data[0];
            this.mTempEntity.mTimeSpan = data[1];
            this.mTempEntity.mValues = new float[data[1] == 0 ? 1 : 1440 / this.mTempEntity.mTimeSpan];
            int i = 0;
            for (int index = 2; index < data.length; ++index) {
                int value = data[index] & 0xFF;
                if (value > 128) {
                    int length = value - 128;
                    for (int temp = 0; temp < length; ++temp) {
                        this.mTempEntity.mValues[i++] = 0.0f;
                    }
                    continue;
                }
                byte temp = data[index];
                float val = (float)temp * 1.0f / 10.0f;
                this.mTempEntity.mValues[i++] = val + 32.0f;
            }
            Log.i((String)TAG, (String)("mTempEntity: " + this.mTempEntity));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Log.i((String)TAG, (String)"===========================ParseReceivedData\u7ed3\u675f============================");
    }

    public void init(HandlerCallback callback) {
        this.mCallback = callback;
    }

    public void startObtainSeries(int index) {
        Log.i((String)TAG, (String)"startObtainSeries... ");
        byte[] data = new byte[]{(byte)index};
        this.mBleManager.execute(this.getWriteRequest(this.addHeader(37, data)));
    }

    @Deprecated
    public void testSend() {
        Log.i((String)TAG, (String)"testSend... ");
        this.mBleManager.execute(this.getWriteRequest(this.addHeader(84, null)));
    }

    private boolean sendNextBigPocket() {
        try {
            if (this.mPocketIndex * 1024 < this.mFileSend.length) {
                byte[] bufferSend = new byte[Math.min(1024, this.mFileSend.length - this.mPocketIndex * 1024)];
                System.arraycopy(this.mFileSend, this.mPocketIndex * 1024, bufferSend, 0, bufferSend.length);
                byte[] compressBuffer = CompressUtils.compress(bufferSend);
                byte[] bufferNext = new byte[compressBuffer.length + 2];
                System.arraycopy(DataTransferUtils.shortToBytes((short)(this.mPocketIndex + 1)), 0, bufferNext, 0, 2);
                System.arraycopy(compressBuffer, 0, bufferNext, 2, compressBuffer.length);
                this.sendPocketToBle(this.addHeader(50, bufferNext));
                this.mPocketIndex = (short)(this.mPocketIndex + 1);
                return true;
            }
            Log.i((String)TAG, (String)"\u6587\u4ef6\u53d1\u9001\u5b8c\u6bd5");
            return false;
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.i((String)TAG, (String)("\u6587\u4ef6\u53d1\u9001\u5f02\u5e38: " + e.getMessage()));
            return false;
        }
    }

    public void cmdSendPacket() {
        this.mPocketIndex = 0;
        this.readNextBigPocket();
    }

    private boolean readNextBigPocket() {
        try {
            if (this.mPocketIndex * 1024 < this.mFileSend.length) {
                byte[] bufferSend = new byte[Math.min(1024, this.mFileSend.length - this.mPocketIndex * 1024)];
                System.arraycopy(this.mFileSend, this.mPocketIndex * 1024, bufferSend, 0, bufferSend.length);
                byte[] compressBuffer = CompressUtils.compress(bufferSend);
                byte[] bufferNext = new byte[compressBuffer.length + 2];
                System.arraycopy(DataTransferUtils.shortToBytes((short)(this.mPocketIndex + 1)), 0, bufferNext, 0, 2);
                System.arraycopy(compressBuffer, 0, bufferNext, 2, compressBuffer.length);
                this.sendPocketToBle(this.addHeader(50, bufferNext));
                this.mPocketIndex = (short)(this.mPocketIndex + 1);
                return true;
            }
            Log.i((String)TAG, (String)"\u6587\u4ef6\u53d1\u9001\u5b8c\u6bd5");
            return false;
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.i((String)TAG, (String)("\u6587\u4ef6\u53d1\u9001\u5f02\u5e38: " + e.getMessage()));
            return false;
        }
    }

    private void sendPocketToBle(byte[] bigPocket) {
        Log.i((String)TAG, (String)("sendPocketToBle: bigPocket=" + DataTransferUtils.getHexString(bigPocket)));
        int index = 0;
        while (index * this.mPackageLength < bigPocket.length) {
            this.mBleManager.execute(this.getWriteRequest(Arrays.copyOfRange(bigPocket, index * this.mPackageLength, index * this.mPackageLength + Math.min(this.mPackageLength, bigPocket.length - index * this.mPackageLength))));
            ++index;
        }
    }

    private void cmdCheck() {
        this.mBleManager.execute(this.getWriteRequest(this.addHeader(51, null)));
    }

    public void endAndRelease() {
    }

    private byte[] addHeader(int cmdId, byte[] data) {
        byte[] pocket = new byte[(data == null ? 0 : data.length) + 6];
        pocket[0] = -68;
        pocket[1] = (byte)cmdId;
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
        WriteRequest noRspInstance = WriteRequest.getNoRspInstance(SERIAL_PORT_SERVICE, SERIAL_PORT_CHARACTER_WRITE);
        noRspInstance.setValue(data);
        return noRspInstance;
    }

    public static interface HandlerCallback {
        public void onProgress(int var1);

        public void onComplete();

        public void onNext(TemperatureEntity var1);

        public void onActionResult(int var1, int var2);
    }
}

