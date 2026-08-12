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
import com.oudmon.ble.base.communication.dfu_temperature.TemperatureEntity;
import com.oudmon.ble.base.communication.dfu_temperature.TemperatureOnceEntity;
import com.oudmon.ble.base.communication.file.DataHelper;
import com.oudmon.ble.base.communication.file.ICallback;
import com.oudmon.ble.base.communication.file.PlateEntity;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import com.oudmon.ble.base.communication.utils.CRC16;
import com.oudmon.ble.base.request.EnableNotifyRequest;
import com.oudmon.ble.base.request.WriteRequest;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public class FileHandle {
    private static final String TAG = "FileHandle";
    public static final int TypeMarketWatchFace = 1;
    public static final int TypeDiyWatchFace = 2;
    public static final int TypeDismissFile = 3;
    public static final int TypeOtaFile = 4;
    private static final byte ACTION_SERIES = 37;
    private static final byte ACTION_ONCE = 38;
    private static final byte ACTION_A_GPS = 84;
    private static final byte ACTION_PLATE = 53;
    private static final UUID SERIAL_PORT_SERVICE = UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_NOTIFY = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_WRITE = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7");
    private EnableNotifyRequest enableNotifyRequest = new EnableNotifyRequest(SERIAL_PORT_SERVICE, SERIAL_PORT_CHARACTER_NOTIFY, new EnableNotifyRequest.ListenerCallback(){

        @Override
        public void enable(boolean result) {
        }
    });
    private CopyOnWriteArraySet<ICallback> mCallbackArray = new CopyOnWriteArraySet();
    private int currFileType = 0;
    private String notDataString = "00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
    private int noDataCount;
    private ICallback mCallback = new ICallback(){

        @Override
        public void onRequestAGPS() {
            for (ICallback callback : FileHandle.this.mCallbackArray) {
                callback.onRequestAGPS();
            }
        }

        @Override
        public void onUpdatePlate(List<PlateEntity> array) {
            for (ICallback callback : FileHandle.this.mCallbackArray) {
                callback.onUpdatePlate(array);
            }
        }

        @Override
        public void onUpdatePlateError(int code) {
            for (ICallback callback : FileHandle.this.mCallbackArray) {
                callback.onUpdatePlateError(code);
            }
        }

        @Override
        public void onDeletePlate() {
            for (ICallback callback : FileHandle.this.mCallbackArray) {
                callback.onDeletePlate();
            }
        }

        @Override
        public void onDeletePlateError(int code) {
            for (ICallback callback : FileHandle.this.mCallbackArray) {
                callback.onDeletePlateError(code);
            }
        }

        @Override
        public void onUpdateTemperature(TemperatureEntity data) {
            for (ICallback callback : FileHandle.this.mCallbackArray) {
                callback.onUpdateTemperature(data);
            }
        }

        @Override
        public void onUpdateTemperatureList(List<TemperatureOnceEntity> array) {
            for (ICallback callback : FileHandle.this.mCallbackArray) {
                callback.onUpdateTemperatureList(array);
            }
        }

        @Override
        public void onFileNames(ArrayList<String> fileNames) {
            for (ICallback callback : FileHandle.this.mCallbackArray) {
                callback.onFileNames(fileNames);
            }
        }

        @Override
        public void onProgress(int percent) {
            for (ICallback callback : FileHandle.this.mCallbackArray) {
                callback.onProgress(percent);
            }
        }

        @Override
        public void onComplete() {
            for (ICallback callback : FileHandle.this.mCallbackArray) {
                callback.onComplete();
            }
        }

        @Override
        public void onActionResult(int type, int errCode) {
            for (ICallback callback : FileHandle.this.mCallbackArray) {
                callback.onActionResult(type, errCode);
            }
        }
    };
    private byte[] mReceivedData;
    private byte[] mFileSend;
    private short mPocketIndex = 0;
    private boolean mPlateReceivedFinished = true;
    private boolean mTemperatureReceivedFinished = true;
    private boolean mTemperatureOnceReceivedFinished = true;
    private boolean mReceiving;
    private int mTotalCount = 0;
    private int mReceivedCount = 0;
    private int mPackageLength;
    private List<String> mFileNames = new ArrayList<String>();
    private static FileHandle mInstance;
    private OnGattEventCallback callback = new OnGattEventCallback(){

        @Override
        public void onReceivedData(String uuid, byte[] data) {
            block65: {
                if (data != null) {
                    if ((data[0] & 0xFF) == 188 && data[1] == 53) {
                        FileHandle.this.mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                        if (FileHandle.this.mTotalCount == 0) {
                            return;
                        }
                        FileHandle.this.mReceivedCount = data.length - 6;
                        FileHandle.access$302(FileHandle.this, new byte[FileHandle.this.mTotalCount]);
                        System.arraycopy(data, 6, FileHandle.this.mReceivedData, 0, FileHandle.this.mReceivedCount);
                        FileHandle.this.mPlateReceivedFinished = FileHandle.this.mReceivedCount >= FileHandle.this.mTotalCount;
                        Log.i((String)FileHandle.TAG, (String)("onReceivedData.. mTotalCount: " + FileHandle.this.mTotalCount + ", mReceivedCount: " + FileHandle.this.mReceivedCount + ", mPlateReceivedFinished: " + FileHandle.this.mPlateReceivedFinished));
                        if (FileHandle.this.mPlateReceivedFinished) {
                            Log.i((String)FileHandle.TAG, (String)("onReceiver All Temperature data: " + DataTransferUtils.getHexString(FileHandle.this.mReceivedData)));
                            if (FileHandle.this.mReceivedData.length > 2) {
                                FileHandle.this.mCallback.onUpdatePlate(DataHelper.parsePlate(FileHandle.this.mReceivedData));
                            } else {
                                ArrayList<PlateEntity> mPlateArray = new ArrayList<PlateEntity>();
                                FileHandle.this.mCallback.onUpdatePlate(mPlateArray);
                            }
                        }
                    } else if (!FileHandle.this.mPlateReceivedFinished) {
                        try {
                            System.arraycopy(data, 0, FileHandle.this.mReceivedData, FileHandle.this.mReceivedCount, data.length);
                            FileHandle.this.mReceivedCount += data.length;
                            FileHandle.this.mPlateReceivedFinished = FileHandle.this.mReceivedCount >= FileHandle.this.mTotalCount;
                            if (!FileHandle.this.mPlateReceivedFinished) break block65;
                            if (FileHandle.this.mReceivedData.length > 2) {
                                FileHandle.this.mCallback.onUpdatePlate(DataHelper.parsePlate(FileHandle.this.mReceivedData));
                                break block65;
                            }
                            ArrayList<PlateEntity> mPlateArray = new ArrayList<PlateEntity>();
                            FileHandle.this.mCallback.onUpdatePlate(mPlateArray);
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 84) {
                        if (data[2] == 0) {
                            FileHandle.this.mCallback.onRequestAGPS();
                        } else if (FileHandle.this.sendNextBigPocket()) {
                            int percent = FileHandle.this.mPocketIndex * 1024 * 100 / FileHandle.this.mFileSend.length;
                            Log.i((String)FileHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u8fdb\u5ea6: " + percent + ", \u5305\u5e8f: " + FileHandle.this.mPocketIndex));
                        } else {
                            Log.i((String)FileHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u5b8c\u6bd5, \u5305\u5e8f: " + FileHandle.this.mPocketIndex));
                            FileHandle.this.cmdCheck();
                        }
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 37) {
                        FileHandle.this.mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                        if (FileHandle.this.mTotalCount == 0) {
                            return;
                        }
                        FileHandle.this.mReceivedCount = data.length - 6;
                        FileHandle.access$302(FileHandle.this, new byte[FileHandle.this.mTotalCount]);
                        System.arraycopy(data, 6, FileHandle.this.mReceivedData, 0, FileHandle.this.mReceivedCount);
                        FileHandle.this.mTemperatureReceivedFinished = FileHandle.this.mReceivedCount >= FileHandle.this.mTotalCount;
                        Log.i((String)FileHandle.TAG, (String)("onReceivedData.. mTotalCount: " + FileHandle.this.mTotalCount + ", mReceivedCount: " + FileHandle.this.mReceivedCount + ", mTemperatureReceivedFinished: " + FileHandle.this.mTemperatureReceivedFinished));
                        if (FileHandle.this.mTemperatureReceivedFinished) {
                            Log.i((String)FileHandle.TAG, (String)("onReceiver All Temperature data: " + DataTransferUtils.getHexString(FileHandle.this.mReceivedData)));
                            if (FileHandle.this.mReceivedData.length > 2) {
                                FileHandle.this.mCallback.onUpdateTemperature(DataHelper.parseTemperature(FileHandle.this.mReceivedData));
                            } else {
                                FileHandle.this.mCallback.onUpdateTemperature(null);
                            }
                        }
                    } else if (!FileHandle.this.mTemperatureReceivedFinished) {
                        System.arraycopy(data, 0, FileHandle.this.mReceivedData, FileHandle.this.mReceivedCount, data.length);
                        FileHandle.this.mReceivedCount += data.length;
                        FileHandle.this.mTemperatureReceivedFinished = FileHandle.this.mReceivedCount >= FileHandle.this.mTotalCount;
                        Log.i((String)FileHandle.TAG, (String)("onReceivedData.. mTotalCount: " + FileHandle.this.mTotalCount + ", mReceivedCount: " + FileHandle.this.mReceivedCount + ", mTemperatureReceivedFinished: " + FileHandle.this.mTemperatureReceivedFinished));
                        if (FileHandle.this.mTemperatureReceivedFinished) {
                            Log.i((String)FileHandle.TAG, (String)("onReceiver All Temperature data: " + DataTransferUtils.getHexString(FileHandle.this.mReceivedData)));
                            if (FileHandle.this.mReceivedData.length > 2) {
                                Log.i((String)FileHandle.TAG, (String)("mCallback: " + FileHandle.this.mCallback + ", class: " + FileHandle.this.mCallback.getClass()));
                                FileHandle.this.mCallback.onUpdateTemperature(DataHelper.parseTemperature(FileHandle.this.mReceivedData));
                            }
                        }
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 38) {
                        FileHandle.this.mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                        if (FileHandle.this.mTotalCount == 0) {
                            return;
                        }
                        FileHandle.this.mReceivedCount = data.length - 6;
                        FileHandle.access$302(FileHandle.this, new byte[FileHandle.this.mTotalCount]);
                        System.arraycopy(data, 6, FileHandle.this.mReceivedData, 0, FileHandle.this.mReceivedCount);
                        FileHandle.this.mTemperatureOnceReceivedFinished = FileHandle.this.mReceivedCount >= FileHandle.this.mTotalCount;
                        Log.i((String)FileHandle.TAG, (String)("onReceivedData.. mTotalCount: " + FileHandle.this.mTotalCount + ", mReceivedCount: " + FileHandle.this.mReceivedCount + ", mTemperatureOnceReceivedFinished: " + FileHandle.this.mTemperatureOnceReceivedFinished));
                        if (FileHandle.this.mTemperatureOnceReceivedFinished) {
                            Log.i((String)FileHandle.TAG, (String)("onReceiver All Temperature once data: " + DataTransferUtils.getHexString(FileHandle.this.mReceivedData)));
                            if (FileHandle.this.mReceivedData.length > 2) {
                                Log.i((String)FileHandle.TAG, (String)("mCallback: " + FileHandle.this.mCallback + ", class: " + FileHandle.this.mCallback.getClass()));
                                FileHandle.this.mCallback.onUpdateTemperatureList(DataHelper.parseTemperatureOnce(FileHandle.this.mReceivedData));
                            } else {
                                FileHandle.this.mCallback.onUpdateTemperatureList(null);
                            }
                        }
                    } else if (!FileHandle.this.mTemperatureOnceReceivedFinished) {
                        System.arraycopy(data, 0, FileHandle.this.mReceivedData, FileHandle.this.mReceivedCount, data.length);
                        FileHandle.this.mReceivedCount += data.length;
                        FileHandle.this.mTemperatureOnceReceivedFinished = FileHandle.this.mReceivedCount >= FileHandle.this.mTotalCount;
                        Log.i((String)FileHandle.TAG, (String)("onReceivedData.. mTotalCount: " + FileHandle.this.mTotalCount + ", mReceivedCount: " + FileHandle.this.mReceivedCount + ", mTemperatureOnceReceivedFinished: " + FileHandle.this.mTemperatureOnceReceivedFinished));
                        if (FileHandle.this.mTemperatureOnceReceivedFinished) {
                            Log.i((String)FileHandle.TAG, (String)("onReceiver All Temperature once data: " + DataTransferUtils.getHexString(FileHandle.this.mReceivedData)));
                            if (FileHandle.this.mReceivedData.length > 2) {
                                Log.i((String)FileHandle.TAG, (String)("mCallback: " + FileHandle.this.mCallback + ", class: " + FileHandle.this.mCallback.getClass()));
                                FileHandle.this.mCallback.onUpdateTemperatureList(DataHelper.parseTemperatureOnce(FileHandle.this.mReceivedData));
                            }
                        }
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 48) {
                        FileHandle.this.mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                        FileHandle.this.mReceivedCount = data.length - 6;
                        FileHandle.access$302(FileHandle.this, new byte[FileHandle.this.mTotalCount]);
                        System.arraycopy(data, 6, FileHandle.this.mReceivedData, 0, FileHandle.this.mReceivedCount);
                        FileHandle.this.mReceiving = FileHandle.this.mReceivedCount < FileHandle.this.mTotalCount;
                        Log.i((String)FileHandle.TAG, (String)("\u6587\u4ef6\uff1a-> 1mTotalCount: " + FileHandle.this.mTotalCount + ", mReceivedCount: " + FileHandle.this.mReceivedCount + ", mReceiving: " + FileHandle.this.mReceiving));
                        if (!FileHandle.this.mReceiving) {
                            Log.i((String)FileHandle.TAG, (String)("\u6587\u4ef6\uff1a->2->" + DataTransferUtils.getHexString(FileHandle.this.mReceivedData)));
                            FileHandle.this.parseFileInfo(FileHandle.this.mReceivedData);
                            FileHandle.this.mCallback.onFileNames((ArrayList)FileHandle.this.mFileNames);
                        }
                    } else if (FileHandle.this.mReceiving) {
                        System.arraycopy(data, 0, FileHandle.this.mReceivedData, FileHandle.this.mReceivedCount, data.length);
                        FileHandle.this.mReceivedCount += data.length;
                        FileHandle.this.mReceiving = FileHandle.this.mReceivedCount < FileHandle.this.mTotalCount;
                        Log.i((String)FileHandle.TAG, (String)("\u6587\u4ef6\uff1a->3 mTotalCount: " + FileHandle.this.mTotalCount + ", mReceivedCount: " + FileHandle.this.mReceivedCount + ", mReceiving: " + FileHandle.this.mReceiving));
                        if (!FileHandle.this.mReceiving) {
                            Log.i((String)FileHandle.TAG, (String)("\u6587\u4ef6\uff1a->4 ->" + DataTransferUtils.getHexString(FileHandle.this.mReceivedData)));
                            FileHandle.this.parseFileInfo(FileHandle.this.mReceivedData);
                            FileHandle.this.mCallback.onFileNames((ArrayList)FileHandle.this.mFileNames);
                        }
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 49) {
                        Log.i((String)FileHandle.TAG, (String)"\u521d\u59cb\u5316\u5b8c\u6210\uff0c\u5f00\u59cb\u5411\u624b\u73af\u53d1\u9001\u5b9e\u9645\u6587\u4ef6");
                        FileHandle.this.cmdSendPacket();
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 50) {
                        if (FileHandle.this.readNextBigPocket()) {
                            int percent = FileHandle.this.mPocketIndex * 1024 * 100 / FileHandle.this.mFileSend.length;
                            Log.i((String)FileHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u8fdb\u5ea6: " + percent + ", \u5305\u5e8f: " + FileHandle.this.mPocketIndex));
                            FileHandle.this.mCallback.onProgress(Math.min(percent, 100));
                        } else {
                            Log.i((String)FileHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u5b8c\u6bd5, \u5305\u5e8f: " + FileHandle.this.mPocketIndex));
                            FileHandle.this.cmdCheck();
                        }
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 51) {
                        Log.i((String)FileHandle.TAG, (String)"===============\u56de\u8c03 onComplete");
                        FileHandle.this.mCallback.onComplete();
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 54) {
                        Log.i((String)FileHandle.TAG, (String)"\u521d\u59cb\u5316\u5b8c\u6210\uff0c\u5f00\u59cb\u5411\u624b\u73af\u53d1\u9001\u5b9e\u9645\u6587\u4ef6");
                        if (data.length > 6 && data[6] > 0) {
                            FileHandle.this.mCallback.onUpdatePlateError(data[6]);
                            return;
                        }
                        FileHandle.this.executeFileSend(55);
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 55) {
                        if (FileHandle.this.executeNextSend(55)) {
                            int percent = FileHandle.this.mPocketIndex * 1024 * 100 / FileHandle.this.mFileSend.length;
                            Log.i((String)FileHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u8fdb\u5ea6: " + percent + ", \u5305\u5e8f: " + FileHandle.this.mPocketIndex));
                            FileHandle.this.mCallback.onProgress(Math.min(percent, 100));
                        } else {
                            Log.i((String)FileHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u5b8c\u6bd5, \u5305\u5e8f: " + FileHandle.this.mPocketIndex));
                            FileHandle.this.executeFileFinished(56);
                        }
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 56) {
                        if (data[6] > 0) {
                            FileHandle.this.mCallback.onUpdatePlateError(666);
                        } else {
                            FileHandle.this.mCallback.onComplete();
                        }
                    } else if ((data[0] & 0xFF) == 188 && data[1] == 57) {
                        if (data.length > 6 && data[6] > 0) {
                            FileHandle.this.mCallback.onDeletePlateError(data[6]);
                            return;
                        }
                        FileHandle.this.mCallback.onDeletePlate();
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
    public static FileHandle getInstance() {
        if (mInstance != null) return mInstance;
        Class<FileHandle> clazz = FileHandle.class;
        synchronized (FileHandle.class) {
            if (mInstance != null) return mInstance;
            mInstance = new FileHandle();
            // ** MonitorExit[var0] (shouldn't be in output)
            return mInstance;
        }
    }

    private FileHandle() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
        Log.i((String)TAG, (String)("create FileHandle.. mPackageLength: " + this.mPackageLength));
    }

    public void initRegister() {
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    public void setDeviceOperateManagerCallback() {
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    private void parseFileInfo(byte[] data) {
        this.mFileNames.clear();
        try {
            byte fileCount = data[0];
            if (fileCount > 0) {
                byte length;
                for (int start = 0; start < data.length - 1; start += length + 1) {
                    length = data[start + 1];
                    byte[] byteName = new byte[length];
                    System.arraycopy(data, start + 2, byteName, 0, length);
                    String fileName = new String(byteName);
                    this.mFileNames.add(fileName);
                    Log.i((String)TAG, (String)("fileLength: " + length + ", fileName: " + fileName));
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void registerCallback(ICallback callback) {
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

    public void startObtainTemperatureSeries(int index) {
        Log.i((String)TAG, (String)"startObtainSeries... ");
        byte[] data = new byte[]{(byte)index};
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(37, data), this.mPackageLength));
    }

    public void startObtainTemperatureOnce(int index) {
        Log.i((String)TAG, (String)"startObtainOnce... ");
        byte[] data = new byte[]{(byte)index};
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(38, data), this.mPackageLength));
    }

    public void startObtainPlate() {
        Log.i((String)TAG, (String)"startObtainPlate... ");
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(53, null), this.mPackageLength));
    }

    public void start() {
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(48, null), this.mPackageLength));
    }

    @Deprecated
    public void testSend() {
        Log.i((String)TAG, (String)"testSend... ");
        BleOperateManager.getInstance().execute(this.getWriteRequest(this.addHeader(84, null)));
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean checkFile(String filePath) {
        Log.i((String)TAG, (String)("\u51c6\u5907\u53d1\u9001\u7684\u6587\u4ef6\u8def\u5f84\uff1a" + filePath));
        File file = new File(filePath);
        if (!file.exists()) {
            Log.i((String)TAG, (String)"\u51c6\u5907\u53d1\u9001\u7684\u6587\u4ef6\u4e0d\u5b58\u5728\uff01");
            return false;
        }
        RandomAccessFile accessFile = null;
        try {
            this.mFileSend = FileHandle.fileToByteStr(filePath);
            Log.i((String)TAG, (String)("\u51c6\u5907\u53d1\u9001\u7684\u6587\u4ef6.. dataSize=" + this.mFileSend.length + "  readSize=" + this.mFileSend.length));
            boolean bl = true;
            return bl;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (accessFile != null) {
                try {
                    accessFile.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    public boolean checkData(byte[] data) {
        Log.i((String)TAG, (String)("checkData... dataSize: " + data.length));
        this.mFileSend = data;
        return true;
    }

    public static byte[] fileToByteStr(String path) {
        byte[] data = null;
        try {
            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
                return data;
            }
            FileInputStream in = new FileInputStream(path);
            data = new byte[((InputStream)in).available()];
            ((InputStream)in).read(data);
            ((InputStream)in).close();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean executeFilePrepare(String path) {
        this.noDataCount = 0;
        File file = new File(path);
        if (!file.exists()) {
            Log.i((String)TAG, (String)"\u51c6\u5907\u53d1\u9001\u7684\u6587\u4ef6\u4e0d\u5b58\u5728\uff01");
            return false;
        }
        RandomAccessFile accessFile = null;
        try {
            accessFile = new RandomAccessFile(path, "r");
            this.mFileSend = new byte[(int)accessFile.length()];
            int size = accessFile.read(this.mFileSend, 0, this.mFileSend.length);
            Log.i((String)TAG, (String)("\u51c6\u5907\u53d1\u9001\u7684\u6587\u4ef6.. dataSize = " + this.mFileSend.length + ", readSize = " + size));
            boolean bl = true;
            return bl;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            if (accessFile != null) {
                try {
                    accessFile.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    public void executeFileInit(String fileName, int cmd) {
        try {
            this.setDeviceOperateManagerCallback();
            Log.i((String)TAG, (String)"executeFileInit.. \u5f00\u59cb");
            byte[] fileNames = fileName.getBytes(StandardCharsets.UTF_8);
            byte[] data = new byte[fileNames.length + 10];
            data[0] = 1;
            System.arraycopy(DataTransferUtils.intToBytes(this.mFileSend.length), 0, data, 1, 4);
            data[9] = (byte)fileNames.length;
            System.arraycopy(fileNames, 0, data, 10, fileNames.length);
            this.sendPocketToBle(this.addHeader(cmd, data));
            Log.i((String)TAG, (String)"executeFileInit.. \u5b8c\u6210");
        }
        catch (Exception e) {
            Log.i((String)TAG, (String)("executeFileInit.. Exception: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    private void executeFileSend(int cmd) {
        this.setDeviceOperateManagerCallback();
        this.mPocketIndex = 0;
        Log.i((String)TAG, (String)("executeFileSend.. \u5f00\u59cb\u53d1\u9001\u6570\u636e\uff0c\u6570\u636e\u957f\u5ea6: " + this.mFileSend.length));
        this.executeNextSend(cmd);
    }

    private boolean executeNextSend(int cmd) {
        try {
            this.setDeviceOperateManagerCallback();
            if (this.mPocketIndex * 1024 < this.mFileSend.length) {
                byte[] bufferSend = new byte[Math.min(1024, this.mFileSend.length - this.mPocketIndex * 1024)];
                System.arraycopy(this.mFileSend, this.mPocketIndex * 1024, bufferSend, 0, bufferSend.length);
                byte[] compressBuffer = CompressUtils.compress(bufferSend);
                byte[] bufferNext = new byte[compressBuffer.length + 2];
                System.arraycopy(DataTransferUtils.shortToBytes((short)(this.mPocketIndex + 1)), 0, bufferNext, 0, 2);
                System.arraycopy(compressBuffer, 0, bufferNext, 2, compressBuffer.length);
                this.sendPocketToBle(this.addHeader(cmd, bufferNext));
                this.mPocketIndex = (short)(this.mPocketIndex + 1);
                if (this.notDataString.equalsIgnoreCase(ByteUtil.byteArrayToString(bufferSend)) && cmd == 55) {
                    ++this.noDataCount;
                    if (this.noDataCount > 30) {
                        Log.i((String)TAG, (String)"\u8868\u76d8\u4e0b\u53d1\u5f02\u5e38\uff0c\u5168\u4e3a0\u7684\u6570\u636e");
                        return false;
                    }
                } else {
                    this.noDataCount = 0;
                }
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

    private void executeFileFinished(int cmd) {
        this.setDeviceOperateManagerCallback();
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(cmd, null), this.mPackageLength));
    }

    public void executeFileDelete(String name) {
        this.setDeviceOperateManagerCallback();
        Log.i((String)TAG, (String)("executeFileDelete.. name: " + name));
        byte[] fileNames = name.getBytes(StandardCharsets.UTF_8);
        Log.i((String)TAG, (String)("executeFileDelete.. fileNames: " + DataTransferUtils.getHexString(fileNames)));
        byte[] data = new byte[fileNames.length + 1];
        data[0] = 1;
        System.arraycopy(fileNames, 0, data, 1, fileNames.length);
        Log.i((String)TAG, (String)("executeFileDelete.. data: " + DataTransferUtils.getHexString(data)));
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(57, data), this.mPackageLength));
    }

    public void executeMusicSend(boolean playing, int progress, int volume, String name) {
        Log.i((String)TAG, (String)("executeMusicSend.. playing: " + playing + ", progress: " + progress + ", volume: " + volume + ", name: " + name));
        byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
        Log.i((String)TAG, (String)("executeMusicSend.. nameBytes: " + DataTransferUtils.getHexString(nameBytes)));
        byte[] data = new byte[nameBytes.length + 3];
        data[0] = (byte)(!playing ? 1 : 0);
        data[1] = (byte)progress;
        data[2] = (byte)volume;
        System.arraycopy(nameBytes, 0, data, 3, nameBytes.length);
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(6, data), this.mPackageLength));
    }

    public void cmdFileInit(String fileName) {
        try {
            this.setDeviceOperateManagerCallback();
            byte[] fileNames = fileName.getBytes("UTF-8");
            byte[] data = new byte[fileNames.length + 10];
            data[0] = 1;
            System.arraycopy(DataTransferUtils.intToBytes(this.mFileSend.length), 0, data, 1, 4);
            data[9] = (byte)fileNames.length;
            System.arraycopy(fileNames, 0, data, 10, fileNames.length);
            this.sendPocketToBle(this.addHeader(49, data));
            Log.i((String)TAG, (String)"cmdFileInit.. \u5b8c\u6210");
        }
        catch (Exception e) {
            Log.i((String)TAG, (String)("cmdFileInit.. Exception: " + e.getMessage()));
            e.printStackTrace();
        }
    }

    public void startGpsOnline() {
        this.mPocketIndex = 0;
        this.sendNextBigPocket();
    }

    private boolean sendNextBigPocket() {
        try {
            this.setDeviceOperateManagerCallback();
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
        this.setDeviceOperateManagerCallback();
        this.mPocketIndex = 0;
        if (this.mFileSend == null) {
            return;
        }
        Log.i((String)TAG, (String)("cmdSendPacket.. \u5f00\u59cb\u53d1\u9001\u6570\u636e\uff0c\u6570\u636e\u957f\u5ea6: " + this.mFileSend.length));
        this.readNextBigPocket();
    }

    private boolean readNextBigPocket() {
        try {
            this.setDeviceOperateManagerCallback();
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
        this.setDeviceOperateManagerCallback();
        this.resetPackageLength();
        BleThreadManager.getInstance().addData(new BleDataBean(bigPocket, this.mPackageLength));
    }

    private void resetPackageLength() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
        Log.i((String)TAG, (String)("resetPackageLength.. mPackageLength: " + this.mPackageLength));
    }

    private void cmdCheck() {
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(51, null), this.mPackageLength));
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

    public WriteRequest getWriteRequest(byte[] data) {
        Log.i((String)TAG, (String)("getWriteRequest: data=" + DataTransferUtils.getHexString(data)));
        WriteRequest noRspInstance = WriteRequest.getNoRspInstance(SERIAL_PORT_SERVICE, SERIAL_PORT_CHARACTER_WRITE);
        noRspInstance.setValue(data);
        return noRspInstance;
    }

    public static String getMD5Three(String path) {
        BigInteger bi = null;
        try {
            byte[] buffer = new byte[8192];
            int len = 0;
            MessageDigest md = MessageDigest.getInstance("MD5");
            File f = new File(path);
            FileInputStream fis = new FileInputStream(f);
            while ((len = fis.read(buffer)) != -1) {
                md.update(buffer, 0, len);
            }
            fis.close();
            byte[] b = md.digest();
            bi = new BigInteger(1, b);
        }
        catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return bi.toString(16);
    }

    static /* synthetic */ byte[] access$302(FileHandle x0, byte[] x1) {
        x0.mReceivedData = x1;
        return x1;
    }
}

