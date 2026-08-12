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
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
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

public class AlbumHandle {
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
    private String logPath = "";
    private String logPath1 = "";
    private IEbookCallback mCallback = new IEbookCallback(){

        @Override
        public void onFileNames(ArrayList<String> fileNames) {
            for (IEbookCallback callback : AlbumHandle.this.mCallbackArray) {
                callback.onFileNames(fileNames);
            }
        }

        @Override
        public void onProgress(float percent) {
            for (IEbookCallback callback : AlbumHandle.this.mCallbackArray) {
                callback.onProgress(percent);
            }
        }

        @Override
        public void onComplete() {
            for (IEbookCallback callback : AlbumHandle.this.mCallbackArray) {
                callback.onComplete();
            }
        }

        @Override
        public void onDeleteSuccess(int code) {
            for (IEbookCallback callback : AlbumHandle.this.mCallbackArray) {
                callback.onDeleteSuccess(code);
            }
        }

        @Override
        public void onActionResult(int errCode) {
            for (IEbookCallback callback : AlbumHandle.this.mCallbackArray) {
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
    private static AlbumHandle mInstance;
    private OnGattEventCallback callback = new OnGattEventCallback(){

        @Override
        public void onReceivedData(String uuid, byte[] data) {
            if (data != null) {
                if ((data[0] & 0xFF) == 188 && data[1] == 49) {
                    Log.i((String)AlbumHandle.TAG, (String)"\u521d\u59cb\u5316\u5b8c\u6210\uff0c\u5f00\u59cb\u5411\u624b\u73af\u53d1\u9001\u5b9e\u9645\u6587\u4ef6");
                    AlbumHandle.this.cmdSendPacket();
                } else if ((data[0] & 0xFF) == 188 && data[1] == 50) {
                    if (AlbumHandle.this.readNextBigPocket()) {
                        float percent = (float)(AlbumHandle.this.mPocketIndex * 1024) * 1.0f * 100.0f / (float)AlbumHandle.this.totalSize;
                        DecimalFormat df = new DecimalFormat("#.00", new DecimalFormatSymbols(Locale.US));
                        percent = Float.parseFloat(df.format(percent));
                        Log.i((String)AlbumHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u8fdb\u5ea6: " + percent + ", \u5305\u5e8f: " + AlbumHandle.this.mPocketIndex + "\u603b\u5305:" + AlbumHandle.this.totalSize));
                        AlbumHandle.this.mCallback.onProgress(Math.min(percent, 100.0f));
                    } else {
                        Log.i((String)AlbumHandle.TAG, (String)("\u5411\u624b\u73af\u53d1\u9001\u6570\u636e\u5b8c\u6bd5, \u5305\u5e8f: " + AlbumHandle.this.mPocketIndex));
                        AlbumHandle.this.cmdCheck();
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == 51) {
                    Log.i((String)AlbumHandle.TAG, (String)"===============\u56de\u8c03 onComplete");
                    AlbumHandle.this.mCallback.onComplete();
                    AlbumHandle.access$602(AlbumHandle.this, new byte[0]);
                } else if ((data[0] & 0xFF) == 188 && data[1] == -128) {
                    int total = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 7, 8));
                    int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 8, 9));
                    if (currIndex == 0) {
                        AlbumHandle.this.fileNames = new ArrayList();
                    }
                    if (total > 0) {
                        int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 10, 11));
                        AlbumHandle.this.parseEbookData(data, 11, offset, total, 0);
                    } else {
                        AlbumHandle.this.mCallback.onFileNames(AlbumHandle.this.fileNames);
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == -127) {
                    int code = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 6, 7));
                    AlbumHandle.this.mCallback.onDeleteSuccess(code);
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
    public static AlbumHandle getInstance() {
        if (mInstance != null) return mInstance;
        Class<AlbumHandle> clazz = AlbumHandle.class;
        synchronized (AlbumHandle.class) {
            if (mInstance != null) return mInstance;
            mInstance = new AlbumHandle();
            // ** MonitorExit[var0] (shouldn't be in output)
            return mInstance;
        }
    }

    private AlbumHandle() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
        Log.i((String)TAG, (String)("create FileHandle.. mPackageLength: " + this.mPackageLength));
    }

    public void initRegister() {
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    public void setDeviceOperateManagerCallback() {
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public boolean executeFilePrepare(String path) {
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

    static /* synthetic */ byte[] access$602(AlbumHandle x0, byte[] x1) {
        x0.mFileSend = x1;
        return x1;
    }
}

