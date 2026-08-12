/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.file;

import android.util.Log;
import com.oudmon.ble.base.bluetooth.BleOperateManager;
import com.oudmon.ble.base.bluetooth.OnGattEventCallback;
import com.oudmon.ble.base.bluetooth.queue.BleDataBean;
import com.oudmon.ble.base.bluetooth.queue.BleThreadManager;
import com.oudmon.ble.base.communication.CompressUtils;
import com.oudmon.ble.base.communication.JPackageManager;
import com.oudmon.ble.base.communication.file.ICallback;
import com.oudmon.ble.base.communication.file.IEbookCallback;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import com.oudmon.ble.base.communication.utils.CRC16;
import com.oudmon.ble.base.request.EnableNotifyRequest;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public class AvatarHandle {
    private static final String TAG = "EbookHandle";
    private static final UUID SERIAL_PORT_SERVICE = UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_NOTIFY = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_WRITE = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7");
    private ArrayList<String> fileNames = new ArrayList();
    private EnableNotifyRequest enableNotifyRequest = new EnableNotifyRequest(SERIAL_PORT_SERVICE, SERIAL_PORT_CHARACTER_NOTIFY, new EnableNotifyRequest.ListenerCallback(){

        @Override
        public void enable(boolean result) {
        }
    });
    private CopyOnWriteArraySet<IEbookCallback> mCallbackArray = new CopyOnWriteArraySet();
    private int currFileType = 0;
    private IEbookCallback mCallback = new IEbookCallback(){

        @Override
        public void onFileNames(ArrayList<String> fileNames) {
            for (IEbookCallback callback : AvatarHandle.this.mCallbackArray) {
                callback.onFileNames(fileNames);
            }
        }

        @Override
        public void onProgress(float percent) {
            for (IEbookCallback callback : AvatarHandle.this.mCallbackArray) {
                callback.onProgress(percent);
            }
        }

        @Override
        public void onComplete() {
            for (IEbookCallback callback : AvatarHandle.this.mCallbackArray) {
                callback.onComplete();
            }
        }

        @Override
        public void onDeleteSuccess(int code) {
            for (IEbookCallback callback : AvatarHandle.this.mCallbackArray) {
                callback.onDeleteSuccess(code);
            }
        }

        @Override
        public void onActionResult(int errCode) {
            for (IEbookCallback callback : AvatarHandle.this.mCallbackArray) {
                callback.onActionResult(errCode);
            }
        }
    };
    private byte[] mFileSend;
    private short mPocketIndex = 0;
    private int totalCount = 1;
    private int totalSize = 1;
    private int mPackageLength;
    private static AvatarHandle mInstance;
    private OnGattEventCallback callback = new OnGattEventCallback(){

        @Override
        public void onReceivedData(String uuid, byte[] data) {
            if (data != null) {
                Log.i((String)AvatarHandle.TAG, (String)ByteUtil.byteArrayToString(data));
                if ((data[0] & 0xFF) == 188 && data[1] == 74) {
                    if (AvatarHandle.this.readNextBigPocket()) {
                        float percent = (float)(AvatarHandle.this.mPocketIndex * 1024) * 1.0f * 100.0f / (float)AvatarHandle.this.totalSize;
                        DecimalFormat df = new DecimalFormat("#.00", new DecimalFormatSymbols(Locale.US));
                        percent = Float.parseFloat(df.format(percent));
                        Log.i((String)AvatarHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u8fdb\u5ea6: " + percent + ", \u5305\u5e8f: " + AvatarHandle.this.mPocketIndex + "\u603b\u5305:" + AvatarHandle.this.totalSize));
                        AvatarHandle.this.mCallback.onProgress(Math.min(percent, 100.0f));
                    } else {
                        AvatarHandle.this.mCallback.onComplete();
                        Log.i((String)AvatarHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u5b8c\u6bd5, \u5305\u5e8f: " + AvatarHandle.this.mPocketIndex));
                    }
                }
            }
        }
    };

    public int getCurrFileType() {
        return this.currFileType;
    }

    public void setCurrFileType(int currFileType) {
        this.currFileType = currFileType;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static AvatarHandle getInstance() {
        if (mInstance != null) return mInstance;
        Class<AvatarHandle> clazz = AvatarHandle.class;
        synchronized (AvatarHandle.class) {
            if (mInstance != null) return mInstance;
            mInstance = new AvatarHandle();
            // ** MonitorExit[var0] (shouldn't be in output)
            return mInstance;
        }
    }

    private AvatarHandle() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
        Log.i((String)TAG, (String)("create FileHandle.. mPackageLength: " + this.mPackageLength));
    }

    public void initRegister() {
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    public void setDeviceOperateManagerCallback() {
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    public String unicodeByteToStr(byte[] bBuf) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bBuf.length; i += 2) {
            int index = ByteUtil.bytesToInt(Arrays.copyOfRange(bBuf, i, i + 2));
            sb.append((char)index);
        }
        return sb.toString();
    }

    public void registerCallback(IEbookCallback callback) {
        try {
            this.mCallbackArray.remove(callback);
            this.mCallbackArray.add(callback);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeCallback(ICallback callback) {
        try {
            this.mCallbackArray.remove(callback);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void clearCallback() {
        try {
            this.mCallbackArray.clear();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cmdSendPacket() {
        this.setDeviceOperateManagerCallback();
        this.mPocketIndex = 0;
        if (this.mFileSend == null) {
            return;
        }
        Log.i((String)TAG, (String)("cmdSendPacket.. \u5f00\u59cb\u53d1\u9001\u6570\u636e\uff0c\u6570\u636e\u957f\u5ea6: " + this.totalSize));
        this.readNextBigPocket();
    }

    private boolean readNextBigPocket() {
        try {
            this.setDeviceOperateManagerCallback();
            if (this.mPocketIndex * 1024 < this.totalSize) {
                if (this.mFileSend.length > 0) {
                    byte[] bufferSend = new byte[Math.min(1024, this.totalSize - this.mPocketIndex * 1024)];
                    System.arraycopy(this.mFileSend, this.mPocketIndex * 1024, bufferSend, 0, bufferSend.length);
                    byte[] compressBuffer = CompressUtils.compress(bufferSend);
                    byte[] bufferNext = new byte[compressBuffer.length + 3];
                    byte[] buff = new byte[]{(byte)this.totalCount, (byte)(this.mPocketIndex + 1), 1};
                    System.arraycopy(buff, 0, bufferNext, 0, 3);
                    System.arraycopy(compressBuffer, 0, bufferNext, 3, compressBuffer.length);
                    this.sendPocketToBle(this.addHeader(74, bufferNext));
                    this.mPocketIndex = (short)(this.mPocketIndex + 1);
                    return true;
                }
            } else {
                Log.i((String)TAG, (String)"\u6587\u4ef6\u53d1\u9001\u5b8c\u6bd5");
            }
            return false;
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.i((String)TAG, (String)("\u6587\u4ef6\u53d1\u9001\u5f02\u5e38: " + e.getMessage()));
            return false;
        }
    }

    private void sendPocketToBle(byte[] bigPocket) {
        this.setDeviceOperateManagerCallback();
        this.resetPackageLength();
        BleThreadManager.getInstance().addData(new BleDataBean(bigPocket, this.mPackageLength));
    }

    private void resetPackageLength() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
        Log.i((String)TAG, (String)("resetPackageLength.. mPackageLength: " + this.mPackageLength));
    }

    public boolean checkData(byte[] data) {
        this.mFileSend = data;
        this.totalSize = data.length;
        int length = this.mFileSend.length;
        this.totalCount = length / 1024;
        if (length % 1024 != 0) {
            ++this.totalCount;
        }
        this.setDeviceOperateManagerCallback();
        Log.i((String)TAG, (String)("\u603b\u5305\u6570: " + this.totalCount));
        return true;
    }

    public void endAndRelease() {
        this.enableNotifyRequest.setEnable(false);
        BleOperateManager.getInstance().execute(this.enableNotifyRequest);
        BleOperateManager.getInstance().setCallback(null);
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
}

