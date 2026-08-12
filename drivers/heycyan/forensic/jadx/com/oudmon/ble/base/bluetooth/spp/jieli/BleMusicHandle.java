/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.bluetooth.spp.jieli;

import android.util.Log;
import com.oudmon.ble.base.bluetooth.BleOperateManager;
import com.oudmon.ble.base.bluetooth.OnGattEventCallback;
import com.oudmon.ble.base.bluetooth.queue.BleDataBean;
import com.oudmon.ble.base.bluetooth.queue.BleThreadManager;
import com.oudmon.ble.base.bluetooth.spp.SerialListener;
import com.oudmon.ble.base.bluetooth.spp.SerialSocket;
import com.oudmon.ble.base.communication.CompressUtils;
import com.oudmon.ble.base.communication.JPackageManager;
import com.oudmon.ble.base.communication.file.ICallback;
import com.oudmon.ble.base.communication.file.IEbookCallback;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import com.oudmon.ble.base.communication.utils.CRC16;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;

public class BleMusicHandle {
    private static final String TAG = "SppHandle";
    public static int FileTypeMuSic = 3;
    private ArrayList<String> fileNames = new ArrayList();
    private CopyOnWriteArraySet<IEbookCallback> mCallbackArray = new CopyOnWriteArraySet();
    private int currFileType = 0;
    private SerialListener outSerialListener;
    private SerialListener serialListener = new SerialListener(){

        @Override
        public void onSerialConnect() {
            if (BleMusicHandle.this.outSerialListener != null) {
                BleMusicHandle.this.outSerialListener.onSerialConnect();
            }
        }

        @Override
        public void onSerialConnectError(Exception e) {
            if (BleMusicHandle.this.outSerialListener != null) {
                BleMusicHandle.this.outSerialListener.onSerialConnectError(e);
            }
        }

        @Override
        public void onSerialRead(byte[] data) {
            if (data != null) {
                Log.i((String)BleMusicHandle.TAG, (String)ByteUtil.byteArrayToString(data));
                if ((data[0] & 0xFF) == 188 && data[1] == 49) {
                    Log.i((String)BleMusicHandle.TAG, (String)"\u521d\u59cb\u5316\u5b8c\u6210\uff0c\u5f00\u59cb\u5411\u624b\u73af\u53d1\u9001\u5b9e\u9645\u6587\u4ef6");
                    BleMusicHandle.this.cmdSendPacket();
                } else if ((data[0] & 0xFF) == 188 && data[1] == 50) {
                    if (BleMusicHandle.this.readNextBigPocket()) {
                        float percent = (float)(BleMusicHandle.this.mPocketIndex * 1024) * 1.0f * 100.0f / (float)BleMusicHandle.this.totalSize;
                        DecimalFormat df = new DecimalFormat("#.00", new DecimalFormatSymbols(Locale.US));
                        percent = Float.parseFloat(df.format(percent));
                        Log.i((String)BleMusicHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u8fdb\u5ea6: " + percent + ", \u5305\u5e8f: " + BleMusicHandle.this.mPocketIndex + "\u603b\u5305:" + BleMusicHandle.this.totalSize));
                        BleMusicHandle.this.mCallback.onProgress(Math.min(percent, 100.0f));
                    } else {
                        Log.i((String)BleMusicHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u5b8c\u6bd5, \u5305\u5e8f: " + BleMusicHandle.this.mPocketIndex));
                        BleMusicHandle.this.cmdCheck();
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == 51) {
                    Log.i((String)BleMusicHandle.TAG, (String)"===============\u56de\u8c03 onComplete");
                    BleMusicHandle.access$602(BleMusicHandle.this, new byte[0]);
                    BleMusicHandle.this.mCallback.onComplete();
                } else if ((data[0] & 0xFF) == 188 && data[1] == -128) {
                    int total = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 7, 8));
                    int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 8, 9));
                    if (currIndex == 0) {
                        BleMusicHandle.this.fileNames = new ArrayList();
                    }
                    if (total > 0) {
                        int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 10, 11));
                        BleMusicHandle.this.parseEbookData(data, 11, offset, total, 0);
                    } else {
                        BleMusicHandle.this.mCallback.onFileNames(BleMusicHandle.this.fileNames);
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == -127) {
                    int code = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 6, 7));
                    BleMusicHandle.this.mCallback.onDeleteSuccess(code);
                }
            }
        }

        @Override
        public void onSerialRead(ArrayDeque<byte[]> datas) {
        }

        @Override
        public void onSerialIoError(Exception e) {
            if (BleMusicHandle.this.outSerialListener != null) {
                BleMusicHandle.this.outSerialListener.onSerialIoError(e);
            }
        }
    };
    private IEbookCallback mCallback = new IEbookCallback(){

        @Override
        public void onFileNames(ArrayList<String> fileNames) {
            for (IEbookCallback callback : BleMusicHandle.this.mCallbackArray) {
                callback.onFileNames(fileNames);
            }
        }

        @Override
        public void onProgress(float percent) {
            for (IEbookCallback callback : BleMusicHandle.this.mCallbackArray) {
                callback.onProgress(percent);
            }
        }

        @Override
        public void onComplete() {
            for (IEbookCallback callback : BleMusicHandle.this.mCallbackArray) {
                callback.onComplete();
            }
        }

        @Override
        public void onDeleteSuccess(int code) {
            for (IEbookCallback callback : BleMusicHandle.this.mCallbackArray) {
                callback.onDeleteSuccess(code);
            }
        }

        @Override
        public void onActionResult(int errCode) {
            for (IEbookCallback callback : BleMusicHandle.this.mCallbackArray) {
                callback.onActionResult(errCode);
            }
        }
    };
    private byte[] mFileSend;
    private byte[] mFileSendA;
    private byte[] mFileSendB;
    private short mPocketIndex = 0;
    private short mPocketIndexA = 0;
    private short mPocketIndexB = 0;
    private int totalSize = 1;
    private int sizeA = 1;
    private int sizeB = 1;
    private int mPackageLength = 244;
    private int mTotalCount = 0;
    private int mReceivedCount = 0;
    private byte[] mReceivedData;
    private boolean mReceiving;
    private static BleMusicHandle mInstance;
    private OnGattEventCallback callback = new OnGattEventCallback(){

        @Override
        public void onReceivedData(String uuid, byte[] data) {
            if (data != null) {
                if ((data[0] & 0xFF) == 188 && data[1] == 49) {
                    Log.i((String)BleMusicHandle.TAG, (String)"\u521d\u59cb\u5316\u5b8c\u6210\uff0c\u5f00\u59cb\u5411\u624b\u73af\u53d1\u9001\u5b9e\u9645\u6587\u4ef6");
                    BleMusicHandle.this.cmdSendPacket();
                } else if ((data[0] & 0xFF) == 188 && data[1] == 50) {
                    int code = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 6, 7));
                    if (code == 0) {
                        if (BleMusicHandle.this.readNextBigPocket()) {
                            float percent = (float)(BleMusicHandle.this.mPocketIndex * 1024) * 1.0f * 100.0f / (float)BleMusicHandle.this.totalSize;
                            DecimalFormat df = new DecimalFormat("#.00", new DecimalFormatSymbols(Locale.US));
                            percent = Float.parseFloat(df.format(percent));
                            Log.i((String)BleMusicHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u8fdb\u5ea6: " + percent + ", \u5305\u5e8f: " + BleMusicHandle.this.mPocketIndex + "\u603b\u5305:" + BleMusicHandle.this.totalSize));
                            BleMusicHandle.this.mCallback.onProgress(Math.min(percent, 100.0f));
                        } else {
                            Log.i((String)BleMusicHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u5b8c\u6bd5, \u5305\u5e8f: " + BleMusicHandle.this.mPocketIndex));
                            BleMusicHandle.this.cmdCheck();
                        }
                    } else {
                        BleMusicHandle.this.mCallback.onDeleteSuccess(code);
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == 51) {
                    Log.i((String)BleMusicHandle.TAG, (String)"===============\u56de\u8c03 onComplete");
                    BleMusicHandle.access$602(BleMusicHandle.this, new byte[0]);
                    BleMusicHandle.this.mCallback.onComplete();
                } else if ((data[0] & 0xFF) == 188 && data[1] == -128) {
                    BleMusicHandle.this.mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                    BleMusicHandle.this.mReceivedCount = data.length - 6;
                    BleMusicHandle.access$1202(BleMusicHandle.this, new byte[BleMusicHandle.this.mTotalCount]);
                    System.arraycopy(data, 6, BleMusicHandle.this.mReceivedData, 0, BleMusicHandle.this.mReceivedCount);
                    BleMusicHandle.this.mReceiving = BleMusicHandle.this.mReceivedCount < BleMusicHandle.this.mTotalCount;
                    Log.i((String)BleMusicHandle.TAG, (String)("\u6587\u4ef6\uff1a-> 1mTotalCount: " + BleMusicHandle.this.mTotalCount + ", mReceivedCount: " + BleMusicHandle.this.mReceivedCount + ", mReceiving: " + BleMusicHandle.this.mReceiving));
                    if (!BleMusicHandle.this.mReceiving) {
                        Log.i((String)BleMusicHandle.TAG, (String)("\u6587\u4ef6\uff1a->2->" + DataTransferUtils.getHexString(BleMusicHandle.this.mReceivedData)));
                        int total = ByteUtil.bytesToInt(Arrays.copyOfRange(BleMusicHandle.this.mReceivedData, 1, 2));
                        int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(BleMusicHandle.this.mReceivedData, 2, 3));
                        if (currIndex == 0) {
                            BleMusicHandle.this.fileNames = new ArrayList();
                        }
                        if (total > 0) {
                            int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(BleMusicHandle.this.mReceivedData, 4, 5));
                            BleMusicHandle.this.parseEbookData(BleMusicHandle.this.mReceivedData, 5, offset, total, 0);
                        } else {
                            BleMusicHandle.this.mCallback.onFileNames(BleMusicHandle.this.fileNames);
                        }
                    }
                } else if (BleMusicHandle.this.mReceiving) {
                    System.arraycopy(data, 0, BleMusicHandle.this.mReceivedData, BleMusicHandle.this.mReceivedCount, data.length);
                    BleMusicHandle.this.mReceivedCount += data.length;
                    BleMusicHandle.this.mReceiving = BleMusicHandle.this.mReceivedCount < BleMusicHandle.this.mTotalCount;
                    Log.i((String)BleMusicHandle.TAG, (String)("\u6587\u4ef6\uff1a->3 mTotalCount: " + BleMusicHandle.this.mTotalCount + ", mReceivedCount: " + BleMusicHandle.this.mReceivedCount + ", mReceiving: " + BleMusicHandle.this.mReceiving));
                    if (!BleMusicHandle.this.mReceiving) {
                        Log.i((String)BleMusicHandle.TAG, (String)("\u6587\u4ef6\uff1a->4 ->" + DataTransferUtils.getHexString(BleMusicHandle.this.mReceivedData)));
                        int total = ByteUtil.bytesToInt(Arrays.copyOfRange(BleMusicHandle.this.mReceivedData, 1, 2));
                        int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(BleMusicHandle.this.mReceivedData, 2, 3));
                        if (currIndex == 0) {
                            BleMusicHandle.this.fileNames = new ArrayList();
                        }
                        if (total > 0) {
                            int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(BleMusicHandle.this.mReceivedData, 4, 5));
                            BleMusicHandle.this.parseEbookData(BleMusicHandle.this.mReceivedData, 5, offset, total, 0);
                        } else {
                            BleMusicHandle.this.mCallback.onFileNames(BleMusicHandle.this.fileNames);
                        }
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == -127) {
                    int code = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 6, 7));
                    BleMusicHandle.this.mCallback.onDeleteSuccess(code);
                }
            }
        }
    };
    boolean mPocketIndexAFlag = false;

    public int getCurrFileType() {
        return this.currFileType;
    }

    public void setCurrFileType(int currFileType) {
        this.currFileType = currFileType;
    }

    public void setOutSerialListener(SerialListener outSerialListener) {
        this.outSerialListener = outSerialListener;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static BleMusicHandle getInstance() {
        if (mInstance != null) return mInstance;
        Class<BleMusicHandle> clazz = BleMusicHandle.class;
        synchronized (BleMusicHandle.class) {
            if (mInstance != null) return mInstance;
            mInstance = new BleMusicHandle();
            // ** MonitorExit[var0] (shouldn't be in output)
            return mInstance;
        }
    }

    private BleMusicHandle() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
        Log.i((String)TAG, (String)("create FileHandle.. mPackageLength: " + this.mPackageLength));
    }

    public void initRegister() {
        this.registerMusicBleCallback();
        SerialSocket.getInstance().setListener(this.serialListener);
    }

    public void registerMusicBleCallback() {
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    private void parseEbookData(byte[] data, int start, int offset, int total, int currIndex) {
        try {
            byte[] nameArrays = Arrays.copyOfRange(data, start, start + offset);
            String name = this.unicodeByteToStr(nameArrays);
            this.fileNames.add(name);
            if (++currIndex < total) {
                this.parseEbookData(data, start + offset + 1, data[start + offset], total, currIndex);
            } else {
                this.mCallback.onFileNames(this.fileNames);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
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

    public void start(int packIndex) {
        byte[] data = new byte[]{1, (byte)packIndex};
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(128, data), this.mPackageLength));
    }

    public void deleteMusic(int position, String fileName) {
        try {
            byte[] deleteBytes = fileName.getBytes(Charset.forName("unicode"));
            if (!ByteUtil.byteArrayToString(deleteBytes).startsWith("fffe")) {
                for (int i = 0; i < deleteBytes.length; i += 2) {
                    int index = ByteUtil.bytesToInt(Arrays.copyOfRange(deleteBytes, i, i + 2));
                    deleteBytes[i] = (byte)ByteUtil.hiword(index);
                    deleteBytes[i + 1] = (byte)ByteUtil.loword(index);
                }
            }
            byte[] data = new byte[]{1, (byte)position, (byte)deleteBytes.length};
            byte[] contactBytes = ByteUtil.concat(data, deleteBytes);
            BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(129, contactBytes), this.mPackageLength));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled aggressive block sorting
     * Enabled unnecessary exception pruning
     * Enabled aggressive exception aggregation
     */
    public boolean executeFilePrepare(String path) {
        if (this.currFileType != FileTypeMuSic) return false;
        File file = new File(path);
        if (!file.exists()) {
            Log.i((String)TAG, (String)"\u51c6\u5907\u53d1\u9001\u7684\u6587\u4ef6\u4e0d\u5b58\u5728\uff01");
            return false;
        }
        try {
            this.mPocketIndexA = 0;
            this.mPocketIndexB = 0;
            this.mPocketIndex = 0;
            RandomAccessFile accessFile = null;
            try {
                accessFile = new RandomAccessFile(path, "r");
                this.mFileSend = new byte[(int)accessFile.length()];
                this.totalSize = this.mFileSend.length;
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
            if (this.totalSize <= 0) return false;
            return true;
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public static String charset(String path) {
        String charset = "GBK";
        byte[] first3Bytes = new byte[3];
        try {
            boolean checked = false;
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(path));
            bis.mark(0);
            int read = bis.read(first3Bytes, 0, 3);
            if (read == -1) {
                bis.close();
                return charset;
            }
            if (first3Bytes[0] == -1 && first3Bytes[1] == -2) {
                charset = "UTF-16LE";
                checked = true;
            } else if (first3Bytes[0] == -2 && first3Bytes[1] == -1) {
                charset = "UTF-16BE";
                checked = true;
            } else if (first3Bytes[0] == -17 && first3Bytes[1] == -69 && first3Bytes[2] == -65) {
                charset = "UTF-8";
                checked = true;
            }
            bis.reset();
            if (!checked) {
                while ((read = bis.read()) != -1 && read < 240 && (128 > read || read > 191)) {
                    if (192 <= read && read <= 223) {
                        read = bis.read();
                        if (128 > read || read > 191) break;
                        continue;
                    }
                    if (224 > read || read > 239) continue;
                    read = bis.read();
                    if (128 > read || read > 191 || 128 > (read = bis.read()) || read > 191) break;
                    charset = "UTF-8";
                    break;
                }
            }
            bis.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return charset;
    }

    public void cmdFileInit(String fileName) {
        try {
            byte[] fileNames = fileName.getBytes(Charset.forName("unicode"));
            if (!ByteUtil.byteArrayToString(fileNames).startsWith("fffe")) {
                for (int i = 0; i < fileNames.length; i += 2) {
                    int index = ByteUtil.bytesToInt(Arrays.copyOfRange(fileNames, i, i + 2));
                    fileNames[i] = (byte)ByteUtil.hiword(index);
                    fileNames[i + 1] = (byte)ByteUtil.loword(index);
                }
            }
            Log.i((String)TAG, (String)ByteUtil.byteArrayToString(fileNames));
            byte[] data = new byte[fileNames.length + 10];
            data[0] = (byte)this.currFileType;
            System.arraycopy(DataTransferUtils.intToBytes(this.totalSize), 0, data, 1, 4);
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

    public void cmdSendPacket() {
        if (this.mFileSend == null) {
            return;
        }
        Log.i((String)TAG, (String)("cmdSendPacket.. \u5f00\u59cb\u53d1\u9001\u6570\u636e\uff0c\u6570\u636e\u957f\u5ea6: " + this.totalSize));
        this.readNextBigPocket();
    }

    private boolean readNextBigPocket() {
        try {
            if (this.mPocketIndex * 1024 < this.totalSize) {
                if (this.mFileSend.length > 0) {
                    byte[] bufferSend = new byte[Math.min(1024, this.totalSize - this.mPocketIndex * 1024)];
                    System.arraycopy(this.mFileSend, this.mPocketIndex * 1024, bufferSend, 0, bufferSend.length);
                    byte[] compressBuffer = CompressUtils.compress(bufferSend);
                    byte[] bufferNext = new byte[compressBuffer.length + 2];
                    System.arraycopy(DataTransferUtils.shortToBytes((short)(this.mPocketIndex + 1)), 0, bufferNext, 0, 2);
                    System.arraycopy(compressBuffer, 0, bufferNext, 2, compressBuffer.length);
                    this.sendPocketToBle(this.addHeader(50, bufferNext));
                    this.mPocketIndex = (short)(this.mPocketIndex + 1);
                    return true;
                }
                if (this.mPocketIndexA * 1024 < this.sizeA) {
                    int minLength = Math.min(1024, this.sizeA - this.mPocketIndexA * 1024);
                    byte[] bufferSend = new byte[minLength];
                    System.arraycopy(this.mFileSendA, this.mPocketIndexA * 1024, bufferSend, 0, bufferSend.length);
                    byte[] compressBuffer = CompressUtils.compress(bufferSend);
                    byte[] bufferNext = new byte[compressBuffer.length + 2];
                    System.arraycopy(DataTransferUtils.shortToBytes((short)(this.mPocketIndexA + 1)), 0, bufferNext, 0, 2);
                    System.arraycopy(compressBuffer, 0, bufferNext, 2, compressBuffer.length);
                    this.sendPocketToBle(this.addHeader(50, bufferNext));
                    this.mPocketIndexA = (short)(this.mPocketIndexA + 1);
                    this.mPocketIndex = (short)(this.mPocketIndexA + this.mPocketIndexB);
                    return true;
                }
                byte[] bufferSend = new byte[Math.min(1024, this.sizeB - this.mPocketIndexB * 1024)];
                System.arraycopy(this.mFileSendB, this.mPocketIndexB * 1024, bufferSend, 0, bufferSend.length);
                byte[] compressBuffer = CompressUtils.compress(bufferSend);
                byte[] bufferNext = new byte[compressBuffer.length + 2];
                System.arraycopy(DataTransferUtils.shortToBytes((short)(this.mPocketIndexA + this.mPocketIndexB + 1)), 0, bufferNext, 0, 2);
                System.arraycopy(compressBuffer, 0, bufferNext, 2, compressBuffer.length);
                this.sendPocketToBle(this.addHeader(50, bufferNext));
                this.mPocketIndexB = (short)(this.mPocketIndexB + 1);
                this.mPocketIndex = (short)(this.mPocketIndexA + this.mPocketIndexB);
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

    public List<List<byte[]>> subListBySegment(List<byte[]> data, int segments) {
        ArrayList<List<byte[]>> result = new ArrayList<List<byte[]>>();
        int size = data.size();
        if (size > 0 && segments > 0) {
            int count = size / segments;
            List<byte[]> cutList = null;
            for (int i = 0; i < segments; ++i) {
                cutList = i == segments - 1 ? data.subList(count * i, size) : data.subList(count * i, count * (i + 1));
                result.add(cutList);
            }
        } else {
            result.add(data);
        }
        return result;
    }

    static /* synthetic */ byte[] access$602(BleMusicHandle x0, byte[] x1) {
        x0.mFileSend = x1;
        return x1;
    }

    static /* synthetic */ byte[] access$1202(BleMusicHandle x0, byte[] x1) {
        x0.mReceivedData = x1;
        return x1;
    }
}

