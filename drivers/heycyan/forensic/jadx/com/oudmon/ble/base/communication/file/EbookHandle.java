/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.Application
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.file;

import android.app.Application;
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
import com.oudmon.qc_utils.date.DateUtil;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public class EbookHandle {
    private static final String TAG = "EbookHandle";
    private static final UUID SERIAL_PORT_SERVICE = UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_NOTIFY = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_WRITE = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7");
    public static int FileTypeEbook = 4;
    public static int FileTypeAlbum = 5;
    public static int FileTypeLYRICS = 6;
    private ArrayList<String> fileNames = new ArrayList();
    private EnableNotifyRequest enableNotifyRequest = new EnableNotifyRequest(SERIAL_PORT_SERVICE, SERIAL_PORT_CHARACTER_NOTIFY, new EnableNotifyRequest.ListenerCallback(){

        @Override
        public void enable(boolean result) {
        }
    });
    private CopyOnWriteArraySet<IEbookCallback> mCallbackArray = new CopyOnWriteArraySet();
    private int currFileType = 0;
    private String logPath = "";
    private String logPath1 = "";
    private IEbookCallback mCallback = new IEbookCallback(){

        @Override
        public void onFileNames(ArrayList<String> fileNames) {
            for (IEbookCallback callback : EbookHandle.this.mCallbackArray) {
                callback.onFileNames(fileNames);
            }
        }

        @Override
        public void onProgress(float percent) {
            for (IEbookCallback callback : EbookHandle.this.mCallbackArray) {
                callback.onProgress(percent);
            }
        }

        @Override
        public void onComplete() {
            for (IEbookCallback callback : EbookHandle.this.mCallbackArray) {
                callback.onComplete();
            }
        }

        @Override
        public void onDeleteSuccess(int code) {
            for (IEbookCallback callback : EbookHandle.this.mCallbackArray) {
                callback.onDeleteSuccess(code);
            }
        }

        @Override
        public void onActionResult(int errCode) {
            for (IEbookCallback callback : EbookHandle.this.mCallbackArray) {
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
    private int mPackageLength;
    private int mTotalCount = 0;
    private int mReceivedCount = 0;
    private byte[] mReceivedData;
    private boolean mReceiving;
    private static EbookHandle mInstance;
    private OnGattEventCallback callback = new OnGattEventCallback(){

        @Override
        public void onReceivedData(String uuid, byte[] data) {
            if (data != null) {
                if ((data[0] & 0xFF) == 188 && data[1] == 49) {
                    Log.i((String)EbookHandle.TAG, (String)"\u521d\u59cb\u5316\u5b8c\u6210\uff0c\u5f00\u59cb\u5411\u624b\u73af\u53d1\u9001\u5b9e\u9645\u6587\u4ef6");
                    if (data[6] > 0) {
                        EbookHandle.this.mCallback.onActionResult(data[6]);
                    } else {
                        EbookHandle.this.cmdSendPacket();
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == 50) {
                    if (EbookHandle.this.readNextBigPocket()) {
                        float percent = (float)(EbookHandle.this.mPocketIndex * 1024) * 1.0f * 100.0f / (float)EbookHandle.this.totalSize;
                        DecimalFormat df = new DecimalFormat("#.00", new DecimalFormatSymbols(Locale.US));
                        percent = Float.parseFloat(df.format(percent));
                        Log.i((String)EbookHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u8fdb\u5ea6: " + percent + ", \u5305\u5e8f: " + EbookHandle.this.mPocketIndex + "\u603b\u5305:" + EbookHandle.this.totalSize));
                        EbookHandle.this.mCallback.onProgress(Math.min(percent, 100.0f));
                    } else {
                        Log.i((String)EbookHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u5b8c\u6bd5, \u5305\u5e8f: " + EbookHandle.this.mPocketIndex));
                        EbookHandle.this.cmdCheck();
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == 51) {
                    Log.i((String)EbookHandle.TAG, (String)"===============\u56de\u8c03 onComplete");
                    EbookHandle.access$602(EbookHandle.this, new byte[0]);
                    if (data[6] > 0) {
                        EbookHandle.this.mCallback.onActionResult(data[6]);
                    } else {
                        EbookHandle.this.mCallback.onComplete();
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == -128) {
                    EbookHandle.this.mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                    EbookHandle.this.mReceivedCount = data.length - 6;
                    EbookHandle.access$902(EbookHandle.this, new byte[EbookHandle.this.mTotalCount]);
                    System.arraycopy(data, 6, EbookHandle.this.mReceivedData, 0, EbookHandle.this.mReceivedCount);
                    EbookHandle.this.mReceiving = EbookHandle.this.mReceivedCount < EbookHandle.this.mTotalCount;
                    Log.i((String)EbookHandle.TAG, (String)("\u6587\u4ef6\uff1a-> 1mTotalCount: " + EbookHandle.this.mTotalCount + ", mReceivedCount: " + EbookHandle.this.mReceivedCount + ", mReceiving: " + EbookHandle.this.mReceiving));
                    if (!EbookHandle.this.mReceiving) {
                        Log.i((String)EbookHandle.TAG, (String)("\u6587\u4ef6\uff1a->2->" + DataTransferUtils.getHexString(EbookHandle.this.mReceivedData)));
                        int total = ByteUtil.bytesToInt(Arrays.copyOfRange(EbookHandle.this.mReceivedData, 1, 2));
                        int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(EbookHandle.this.mReceivedData, 2, 3));
                        if (currIndex == 0) {
                            EbookHandle.this.fileNames = new ArrayList();
                        }
                        if (total > 0) {
                            int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(EbookHandle.this.mReceivedData, 4, 5));
                            EbookHandle.this.parseEbookData(EbookHandle.this.mReceivedData, 5, offset, total, 0);
                        } else {
                            EbookHandle.this.mCallback.onFileNames(EbookHandle.this.fileNames);
                        }
                    }
                } else if (EbookHandle.this.mReceiving) {
                    System.arraycopy(data, 0, EbookHandle.this.mReceivedData, EbookHandle.this.mReceivedCount, data.length);
                    EbookHandle.this.mReceivedCount += data.length;
                    EbookHandle.this.mReceiving = EbookHandle.this.mReceivedCount < EbookHandle.this.mTotalCount;
                    Log.i((String)EbookHandle.TAG, (String)("\u6587\u4ef6\uff1a->3 mTotalCount: " + EbookHandle.this.mTotalCount + ", mReceivedCount: " + EbookHandle.this.mReceivedCount + ", mReceiving: " + EbookHandle.this.mReceiving));
                    if (!EbookHandle.this.mReceiving) {
                        Log.i((String)EbookHandle.TAG, (String)("\u6587\u4ef6\uff1a->4 ->" + DataTransferUtils.getHexString(EbookHandle.this.mReceivedData)));
                        int total = ByteUtil.bytesToInt(Arrays.copyOfRange(EbookHandle.this.mReceivedData, 1, 2));
                        int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(EbookHandle.this.mReceivedData, 2, 3));
                        if (currIndex == 0) {
                            EbookHandle.this.fileNames = new ArrayList();
                        }
                        if (total > 0) {
                            int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(EbookHandle.this.mReceivedData, 4, 5));
                            EbookHandle.this.parseEbookData(EbookHandle.this.mReceivedData, 5, offset, total, 0);
                        } else {
                            EbookHandle.this.mCallback.onFileNames(EbookHandle.this.fileNames);
                        }
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == -127) {
                    int code = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 6, 7));
                    EbookHandle.this.mCallback.onDeleteSuccess(code);
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

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static EbookHandle getInstance() {
        if (mInstance != null) return mInstance;
        Class<EbookHandle> clazz = EbookHandle.class;
        synchronized (EbookHandle.class) {
            if (mInstance != null) return mInstance;
            mInstance = new EbookHandle();
            // ** MonitorExit[var0] (shouldn't be in output)
            return mInstance;
        }
    }

    private EbookHandle() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
        Log.i((String)TAG, (String)("create FileHandle.. mPackageLength: " + this.mPackageLength));
    }

    public void initRegister() {
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    public void setDeviceOperateManagerCallback() {
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
        byte[] data = new byte[]{2, (byte)packIndex};
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(128, data), this.mPackageLength));
    }

    public void deleteEbook(int position, String fileName) {
        try {
            byte[] deleteBytes = fileName.getBytes(Charset.forName("unicode"));
            if (!ByteUtil.byteArrayToString(deleteBytes).startsWith("fffe")) {
                for (int i = 0; i < deleteBytes.length; i += 2) {
                    int index = ByteUtil.bytesToInt(Arrays.copyOfRange(deleteBytes, i, i + 2));
                    deleteBytes[i] = (byte)ByteUtil.hiword(index);
                    deleteBytes[i + 1] = (byte)ByteUtil.loword(index);
                }
            }
            byte[] data = new byte[]{2, (byte)position, (byte)deleteBytes.length};
            byte[] contactBytes = ByteUtil.concat(data, deleteBytes);
            BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(129, contactBytes), this.mPackageLength));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean executeFilePrepare(String path) {
        if (this.currFileType == FileTypeEbook || this.currFileType == FileTypeLYRICS) {
            File file = new File(path);
            if (!file.exists()) {
                Log.i((String)TAG, (String)"\u51c6\u5907\u53d1\u9001\u7684\u6587\u4ef6\u4e0d\u5b58\u5728\uff01");
                return false;
            }
            String charSet = EbookHandle.charset(path);
            Log.i((String)TAG, (String)charSet);
            BufferedReader reader = null;
            StringBuffer sbf = new StringBuffer();
            try {
                String tempStr;
                FileInputStream inputStream = new FileInputStream(path);
                InputStreamReader reader1 = new InputStreamReader((InputStream)inputStream, charSet);
                reader = new BufferedReader(reader1);
                while ((tempStr = reader.readLine()) != null) {
                    sbf.append(tempStr).append("\n");
                }
                reader.close();
                String book = sbf.toString();
                this.mPocketIndexA = 0;
                this.mPocketIndexB = 0;
                this.mPocketIndex = 0;
                if (file.length() > 0xA00000L) {
                    int index;
                    int i;
                    this.mFileSend = new byte[0];
                    int length = book.length() / 2;
                    String b1 = book.substring(0, length);
                    String b2 = book.substring(length);
                    this.mFileSendA = b1.getBytes(Charset.forName("unicode"));
                    this.mFileSendB = b2.getBytes(Charset.forName("unicode"));
                    this.sizeA = this.mFileSendA.length;
                    this.sizeB = this.mFileSendB.length;
                    if (!ByteUtil.byteArraySubToString(this.mFileSendA).startsWith("fffe")) {
                        for (i = 0; i < this.sizeA; i += 2) {
                            index = ByteUtil.bytesToInt(Arrays.copyOfRange(this.mFileSendA, i, i + 2));
                            this.mFileSendA[i] = (byte)ByteUtil.hiword(index);
                            this.mFileSendA[i + 1] = (byte)ByteUtil.loword(index);
                        }
                    }
                    if (!ByteUtil.byteArraySubToString(this.mFileSendB).startsWith("fffe")) {
                        for (i = 2; i < this.sizeB; i += 2) {
                            index = ByteUtil.bytesToInt(Arrays.copyOfRange(this.mFileSendB, i, i + 2));
                            this.mFileSendB[i - 2] = (byte)ByteUtil.hiword(index);
                            this.mFileSendB[i - 1] = (byte)ByteUtil.loword(index);
                        }
                    }
                    int a = 1024 - this.sizeA % 1024;
                    byte[] tempA = new byte[a];
                    System.arraycopy(this.mFileSendB, 0, tempA, 0, a);
                    byte[] tempB = new byte[this.mFileSendB.length - a];
                    System.arraycopy(this.mFileSendB, 0, tempB, 0, this.mFileSendB.length - a);
                    this.mFileSendA = ByteUtil.concat(this.mFileSendA, tempA);
                    this.sizeA = this.mFileSendA.length;
                    this.mFileSendB = tempB;
                    this.sizeB = this.mFileSendB.length;
                    Log.i((String)TAG, (String)(this.sizeA + "----" + this.sizeB));
                    this.totalSize = this.sizeA + this.sizeB;
                } else {
                    this.mFileSend = book.getBytes(Charset.forName("unicode"));
                    this.totalSize = this.mFileSend.length;
                    if (!ByteUtil.byteArraySubToString(this.mFileSend).startsWith("fffe")) {
                        for (int i = 0; i < this.totalSize; i += 2) {
                            int index = ByteUtil.bytesToInt(Arrays.copyOfRange(this.mFileSend, i, i + 2));
                            this.mFileSend[i] = (byte)ByteUtil.hiword(index);
                            this.mFileSend[i + 1] = (byte)ByteUtil.loword(index);
                        }
                    }
                }
                if (this.totalSize > 0) {
                    boolean bl = true;
                    return bl;
                }
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            finally {
                if (reader != null) {
                    try {
                        reader.close();
                    }
                    catch (IOException e1) {
                        e1.printStackTrace();
                    }
                }
            }
            return false;
        }
        if (this.currFileType == FileTypeAlbum) {
            File file = new File(path);
            if (!file.exists()) {
                Log.i((String)TAG, (String)"\u51c6\u5907\u53d1\u9001\u7684\u6587\u4ef6\u4e0d\u5b58\u5728\uff01");
                return false;
            }
            RandomAccessFile accessFile = null;
            try {
                accessFile = new RandomAccessFile(path, "r");
                this.mFileSend = new byte[(int)accessFile.length()];
                this.totalSize = this.mFileSend.length;
                this.mPocketIndex = 0;
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
        return false;
    }

    public boolean executeFilePrepareAlbum(byte[] array) {
        try {
            this.mFileSend = array;
            this.totalSize = this.mFileSend.length;
            this.mPocketIndex = 0;
            Log.i((String)TAG, (String)("\u51c6\u5907\u53d1\u9001\u7684\u6587\u4ef6.. dataSize = " + this.mFileSend.length + ", readSize = " + this.totalSize));
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
            this.setDeviceOperateManagerCallback();
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
        this.setDeviceOperateManagerCallback();
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

    public void initPath(Application application) {
        this.logPath = application.getExternalFilesDir("") + "/log/" + new DateUtil().getY_M_D() + "1_log.txt";
        this.logPath1 = application.getExternalFilesDir("") + "/log/" + new DateUtil().getY_M_D() + "2_log.txt";
    }

    static /* synthetic */ byte[] access$602(EbookHandle x0, byte[] x1) {
        x0.mFileSend = x1;
        return x1;
    }

    static /* synthetic */ byte[] access$902(EbookHandle x0, byte[] x1) {
        x0.mReceivedData = x1;
        return x1;
    }
}

