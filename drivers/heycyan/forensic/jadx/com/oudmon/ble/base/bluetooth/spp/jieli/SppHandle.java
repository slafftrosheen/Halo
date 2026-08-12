/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.bluetooth.BluetoothDevice
 *  android.util.Log
 */
package com.oudmon.ble.base.bluetooth.spp.jieli;

import android.bluetooth.BluetoothDevice;
import android.util.Log;
import com.oudmon.ble.base.bluetooth.BleOperateManager;
import com.oudmon.ble.base.bluetooth.OnGattEventCallback;
import com.oudmon.ble.base.bluetooth.spp.SerialListener;
import com.oudmon.ble.base.bluetooth.spp.SerialSocket;
import com.oudmon.ble.base.communication.CompressUtils;
import com.oudmon.ble.base.communication.file.ICallback;
import com.oudmon.ble.base.communication.file.IEbookCallback;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import com.oudmon.ble.base.communication.utils.CRC16;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;

public class SppHandle {
    private static final String TAG = "GLASSES_LOG";
    public static int FileTypeMuSic = 3;
    private ArrayList<String> fileNames = new ArrayList();
    private CopyOnWriteArraySet<IEbookCallback> mCallbackArray = new CopyOnWriteArraySet();
    private int currFileType = 0;
    private SerialListener outSerialListener;
    private int mTotalCount = 0;
    private int mReceivedCount = 0;
    private byte[] mReceivedData;
    private boolean mReceiving;
    private byte[] mFileSend;
    private byte[] mFileSendA;
    private byte[] mFileSendB;
    private short mPocketIndex = 0;
    private short mPocketIndexA = 0;
    private short mPocketIndexB = 0;
    private int totalSize = 1;
    private int sizeA = 1;
    private int sizeB = 1;
    private int mPackageLength = 512;
    public static int PACKAGE_LENGTH = 512;
    public static int LongBagLength = 1024;
    private static SppHandle mInstance;
    private SerialListener serialListener = new SerialListener(){

        @Override
        public void onSerialConnect() {
            if (SppHandle.this.outSerialListener != null) {
                SppHandle.this.outSerialListener.onSerialConnect();
            }
        }

        @Override
        public void onSerialConnectError(Exception e) {
            if (SppHandle.this.outSerialListener != null) {
                SppHandle.this.outSerialListener.onSerialConnectError(e);
            }
        }

        @Override
        public void onSerialRead(byte[] data) {
            if (data != null) {
                if ((data[0] & 0xFF) == 188 && data[1] == 49) {
                    Log.i((String)SppHandle.TAG, (String)"\u521d\u59cb\u5316\u5b8c\u6210\uff0c\u5f00\u59cb\u5411\u624b\u73af\u53d1\u9001\u5b9e\u9645\u6587\u4ef6");
                    SppHandle.this.cmdSendPacket();
                } else if ((data[0] & 0xFF) == 188 && data[1] == 50) {
                    Log.i((String)SppHandle.TAG, (String)ByteUtil.byteArrayToString(data));
                    int code = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 6, 7));
                    if (code == 0) {
                        if (SppHandle.this.readNextBigPocket()) {
                            float percent = (float)(SppHandle.this.mPocketIndex * LongBagLength) * 1.0f * 100.0f / (float)SppHandle.this.totalSize;
                            DecimalFormat df = new DecimalFormat("#.00", new DecimalFormatSymbols(Locale.US));
                            percent = Float.parseFloat(df.format(percent));
                            Log.i((String)SppHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u8fdb\u5ea6: " + percent + ", \u5305\u5e8f: " + SppHandle.this.mPocketIndex + "\u603b\u5305:" + SppHandle.this.totalSize));
                            SppHandle.this.mCallback.onProgress(Math.min(percent, 100.0f));
                        } else {
                            Log.i((String)SppHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u5b8c\u6bd5, \u5305\u5e8f: " + SppHandle.this.mPocketIndex));
                            SppHandle.this.cmdCheck();
                        }
                    } else {
                        SppHandle.this.mCallback.onDeleteSuccess(code);
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == 51) {
                    Log.i((String)SppHandle.TAG, (String)"===============\u56de\u8c03 onComplete");
                    SppHandle.access$602(SppHandle.this, new byte[0]);
                    SppHandle.this.mCallback.onComplete();
                } else if ((data[0] & 0xFF) == 188 && data[1] == -128) {
                    SppHandle.this.mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                    SppHandle.this.mReceivedCount = data.length - 6;
                    SppHandle.access$902(SppHandle.this, new byte[SppHandle.this.mTotalCount]);
                    System.arraycopy(data, 6, SppHandle.this.mReceivedData, 0, SppHandle.this.mReceivedCount);
                    SppHandle.this.mReceiving = SppHandle.this.mReceivedCount < SppHandle.this.mTotalCount;
                    if (!SppHandle.this.mReceiving) {
                        int total = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 1, 2));
                        int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 2, 3));
                        if (currIndex == 0) {
                            SppHandle.this.fileNames = new ArrayList();
                        }
                        if (total > 0) {
                            int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 4, 5));
                            SppHandle.this.parseEbookData(SppHandle.this.mReceivedData, 5, offset, total, 0);
                        } else {
                            SppHandle.this.mCallback.onFileNames(SppHandle.this.fileNames);
                        }
                    }
                } else if (SppHandle.this.mReceiving) {
                    System.arraycopy(data, 0, SppHandle.this.mReceivedData, SppHandle.this.mReceivedCount, data.length);
                    SppHandle.this.mReceivedCount += data.length;
                    SppHandle.this.mReceiving = SppHandle.this.mReceivedCount < SppHandle.this.mTotalCount;
                    if (!SppHandle.this.mReceiving) {
                        int total = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 1, 2));
                        int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 2, 3));
                        if (currIndex == 0) {
                            SppHandle.this.fileNames = new ArrayList();
                        }
                        if (total > 0) {
                            int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 4, 5));
                            SppHandle.this.parseEbookData(SppHandle.this.mReceivedData, 5, offset, total, 0);
                        } else {
                            SppHandle.this.mCallback.onFileNames(SppHandle.this.fileNames);
                        }
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == -127) {
                    int code = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 6, 7));
                    SppHandle.this.mCallback.onDeleteSuccess(code);
                }
            }
        }

        @Override
        public void onSerialRead(ArrayDeque<byte[]> datas) {
        }

        @Override
        public void onSerialIoError(Exception e) {
            if (SppHandle.this.outSerialListener != null) {
                SppHandle.this.outSerialListener.onSerialIoError(e);
            }
        }
    };
    private IEbookCallback mCallback = new IEbookCallback(){

        @Override
        public void onFileNames(ArrayList<String> fileNames) {
            for (IEbookCallback callback : SppHandle.this.mCallbackArray) {
                callback.onFileNames(fileNames);
            }
        }

        @Override
        public void onProgress(float percent) {
            for (IEbookCallback callback : SppHandle.this.mCallbackArray) {
                callback.onProgress(percent);
            }
        }

        @Override
        public void onComplete() {
            for (IEbookCallback callback : SppHandle.this.mCallbackArray) {
                callback.onComplete();
            }
        }

        @Override
        public void onDeleteSuccess(int code) {
            for (IEbookCallback callback : SppHandle.this.mCallbackArray) {
                callback.onDeleteSuccess(code);
            }
        }

        @Override
        public void onActionResult(int errCode) {
            for (IEbookCallback callback : SppHandle.this.mCallbackArray) {
                callback.onActionResult(errCode);
            }
        }
    };
    private OnGattEventCallback callback = new OnGattEventCallback(){

        @Override
        public void onReceivedData(String uuid, byte[] data) {
            if (data != null) {
                if ((data[0] & 0xFF) == 188 && data[1] == 49) {
                    Log.i((String)SppHandle.TAG, (String)"\u521d\u59cb\u5316\u5b8c\u6210\uff0c\u5f00\u59cb\u5411\u624b\u73af\u53d1\u9001\u5b9e\u9645\u6587\u4ef6");
                    SppHandle.this.cmdSendPacket();
                } else if ((data[0] & 0xFF) == 188 && data[1] == 50) {
                    int code = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 6, 7));
                    if (code == 0) {
                        if (SppHandle.this.readNextBigPocket()) {
                            float percent = (float)(SppHandle.this.mPocketIndex * LongBagLength) * 1.0f * 100.0f / (float)SppHandle.this.totalSize;
                            DecimalFormat df = new DecimalFormat("#.00", new DecimalFormatSymbols(Locale.US));
                            percent = Float.parseFloat(df.format(percent));
                            Log.i((String)SppHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u8fdb\u5ea6: " + percent + ", \u5305\u5e8f: " + SppHandle.this.mPocketIndex + "\u603b\u5305:" + SppHandle.this.totalSize));
                            SppHandle.this.mCallback.onProgress(Math.min(percent, 100.0f));
                        } else {
                            Log.i((String)SppHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u5b8c\u6bd5, \u5305\u5e8f: " + SppHandle.this.mPocketIndex));
                            SppHandle.this.cmdCheck();
                        }
                    } else {
                        SppHandle.this.mCallback.onDeleteSuccess(code);
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == 51) {
                    Log.i((String)SppHandle.TAG, (String)"===============\u56de\u8c03 onComplete");
                    SppHandle.access$602(SppHandle.this, new byte[0]);
                    SppHandle.this.mCallback.onComplete();
                } else if ((data[0] & 0xFF) == 188 && data[1] == -128) {
                    SppHandle.this.mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                    SppHandle.this.mReceivedCount = data.length - 6;
                    SppHandle.access$902(SppHandle.this, new byte[SppHandle.this.mTotalCount]);
                    System.arraycopy(data, 6, SppHandle.this.mReceivedData, 0, SppHandle.this.mReceivedCount);
                    SppHandle.this.mReceiving = SppHandle.this.mReceivedCount < SppHandle.this.mTotalCount;
                    Log.i((String)SppHandle.TAG, (String)("\u6587\u4ef6\uff1a-> 1mTotalCount: " + SppHandle.this.mTotalCount + ", mReceivedCount: " + SppHandle.this.mReceivedCount + ", mReceiving: " + SppHandle.this.mReceiving));
                    if (!SppHandle.this.mReceiving) {
                        Log.i((String)SppHandle.TAG, (String)("\u6587\u4ef6\uff1a->2->" + DataTransferUtils.getHexString(SppHandle.this.mReceivedData)));
                        int total = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 1, 2));
                        int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 2, 3));
                        if (currIndex == 0) {
                            SppHandle.this.fileNames = new ArrayList();
                        }
                        if (total > 0) {
                            int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 4, 5));
                            SppHandle.this.parseEbookData(SppHandle.this.mReceivedData, 5, offset, total, 0);
                        } else {
                            SppHandle.this.mCallback.onFileNames(SppHandle.this.fileNames);
                        }
                    }
                } else if (SppHandle.this.mReceiving) {
                    System.arraycopy(data, 0, SppHandle.this.mReceivedData, SppHandle.this.mReceivedCount, data.length);
                    SppHandle.this.mReceivedCount += data.length;
                    SppHandle.this.mReceiving = SppHandle.this.mReceivedCount < SppHandle.this.mTotalCount;
                    Log.i((String)SppHandle.TAG, (String)("\u6587\u4ef6\uff1a->3 mTotalCount: " + SppHandle.this.mTotalCount + ", mReceivedCount: " + SppHandle.this.mReceivedCount + ", mReceiving: " + SppHandle.this.mReceiving));
                    if (!SppHandle.this.mReceiving) {
                        Log.i((String)SppHandle.TAG, (String)("\u6587\u4ef6\uff1a->4 ->" + DataTransferUtils.getHexString(SppHandle.this.mReceivedData)));
                        int total = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 1, 2));
                        int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 2, 3));
                        if (currIndex == 0) {
                            SppHandle.this.fileNames = new ArrayList();
                        }
                        if (total > 0) {
                            int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(SppHandle.this.mReceivedData, 4, 5));
                            SppHandle.this.parseEbookData(SppHandle.this.mReceivedData, 5, offset, total, 0);
                        } else {
                            SppHandle.this.mCallback.onFileNames(SppHandle.this.fileNames);
                        }
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == -127) {
                    int code = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 6, 7));
                    SppHandle.this.mCallback.onDeleteSuccess(code);
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
    public static SppHandle getInstance() {
        if (mInstance != null) return mInstance;
        Class<SppHandle> clazz = SppHandle.class;
        synchronized (SppHandle.class) {
            if (mInstance != null) return mInstance;
            mInstance = new SppHandle();
            // ** MonitorExit[var0] (shouldn't be in output)
            return mInstance;
        }
    }

    private SppHandle() {
        this.mPackageLength = PACKAGE_LENGTH;
        Log.i((String)TAG, (String)("create FileHandle.. mPackageLength: " + this.mPackageLength));
    }

    public void initRegister() {
        this.registerMusicBleCallback();
        SerialSocket.getInstance().setListener(this.serialListener);
    }

    public void registerMusicBleCallback() {
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    public void cleanSppListener() {
        SerialSocket.getInstance().setListener(null);
        BleOperateManager.getInstance().setCallback(null);
    }

    public void connect(BluetoothDevice device) {
        SerialSocket.getInstance().setDevice(device);
        SerialSocket.getInstance().connect(this.serialListener);
    }

    public void disconnect() {
        SerialSocket.getInstance().disconnect();
    }

    public boolean isConnected() {
        boolean flag = false;
        try {
            flag = SerialSocket.getInstance().isConnected();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return flag;
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
        SerialSocket.getInstance().write(this.addHeader(128, data));
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
            SerialSocket.getInstance().write(this.addHeader(129, contactBytes));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Exception decompiling
     */
    public boolean executeFilePrepare(String path) {
        /*
         * This method has failed to decompile.  When submitting a bug report, please provide this stack trace, and (if you hold appropriate legal rights) the relevant class file.
         * 
         * org.benf.cfr.reader.util.ConfusedCFRException: Tried to end blocks [15[CATCHBLOCK]], but top level block is 35[SIMPLE_IF_TAKEN]
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.processEndingBlocks(Op04StructuredStatement.java:435)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement.buildNestedBlocks(Op04StructuredStatement.java:484)
         *     at org.benf.cfr.reader.bytecode.analysis.opgraph.Op03SimpleStatement.createInitialStructuredBlock(Op03SimpleStatement.java:736)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisInner(CodeAnalyser.java:850)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysisOrWrapFail(CodeAnalyser.java:278)
         *     at org.benf.cfr.reader.bytecode.CodeAnalyser.getAnalysis(CodeAnalyser.java:201)
         *     at org.benf.cfr.reader.entities.attributes.AttributeCode.analyse(AttributeCode.java:94)
         *     at org.benf.cfr.reader.entities.Method.analyse(Method.java:531)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseMid(ClassFile.java:1055)
         *     at org.benf.cfr.reader.entities.ClassFile.analyseTop(ClassFile.java:942)
         *     at org.benf.cfr.reader.Driver.doJarVersionTypes(Driver.java:257)
         *     at org.benf.cfr.reader.Driver.doJar(Driver.java:139)
         *     at org.benf.cfr.reader.CfrDriverImpl.analyse(CfrDriverImpl.java:76)
         *     at org.benf.cfr.reader.Main.main(Main.java:54)
         */
        throw new IllegalStateException("Decompilation failed");
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
            if (this.mPocketIndex * LongBagLength < this.totalSize) {
                if (this.mFileSend.length > 0) {
                    byte[] bufferSend = new byte[Math.min(LongBagLength, this.totalSize - this.mPocketIndex * LongBagLength)];
                    System.arraycopy(this.mFileSend, this.mPocketIndex * LongBagLength, bufferSend, 0, bufferSend.length);
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
        int index = 0;
        while (index * this.mPackageLength < bigPocket.length) {
            SerialSocket.getInstance().write(Arrays.copyOfRange(bigPocket, index * this.mPackageLength, index * this.mPackageLength + Math.min(this.mPackageLength, bigPocket.length - index * this.mPackageLength)));
            ++index;
        }
    }

    private void resetPackageLength() {
        this.mPackageLength = PACKAGE_LENGTH;
        Log.i((String)TAG, (String)("resetPackageLength.. mPackageLength: " + this.mPackageLength));
    }

    private void cmdCheck() {
        SerialSocket.getInstance().write(this.addHeader(51, null));
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

    static /* synthetic */ byte[] access$602(SppHandle x0, byte[] x1) {
        x0.mFileSend = x1;
        return x1;
    }

    static /* synthetic */ byte[] access$902(SppHandle x0, byte[] x1) {
        x0.mReceivedData = x1;
        return x1;
    }
}

