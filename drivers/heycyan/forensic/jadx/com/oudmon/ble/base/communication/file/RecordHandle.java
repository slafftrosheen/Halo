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
import com.oudmon.ble.base.communication.JPackageManager;
import com.oudmon.ble.base.communication.entity.RecordEntity;
import com.oudmon.ble.base.communication.file.ICallback;
import com.oudmon.ble.base.communication.file.IRecordCallback;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import com.oudmon.ble.base.communication.utils.CRC16;
import com.oudmon.ble.base.request.EnableNotifyRequest;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

public class RecordHandle {
    private static final String TAG = "RecondHandle";
    private static final UUID SERIAL_PORT_SERVICE = UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_NOTIFY = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_WRITE = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7");
    private ArrayList<RecordEntity> fileNames = new ArrayList();
    private EnableNotifyRequest enableNotifyRequest = new EnableNotifyRequest(SERIAL_PORT_SERVICE, SERIAL_PORT_CHARACTER_NOTIFY, new EnableNotifyRequest.ListenerCallback(){

        @Override
        public void enable(boolean result) {
        }
    });
    private CopyOnWriteArraySet<IRecordCallback> mCallbackArray = new CopyOnWriteArraySet();
    private int currFileType = 0;
    private int mReceivedCount = 0;
    private int mTotalCount = 0;
    private boolean mReceiving;
    private byte[] mDetails;
    private String fileName;
    private int mReceivedCountName = 0;
    private int mTotalCountName = 0;
    private boolean mReceivingName;
    private byte[] mDetailsName;
    private IRecordCallback mCallback = new IRecordCallback(){

        @Override
        public void onFileNames(ArrayList<RecordEntity> fileNames) {
            for (IRecordCallback callback : RecordHandle.this.mCallbackArray) {
                callback.onFileNames(fileNames);
            }
        }

        @Override
        public void onProgress(float percent) {
            for (IRecordCallback callback : RecordHandle.this.mCallbackArray) {
                callback.onProgress(percent);
            }
        }

        @Override
        public void onComplete() {
            for (IRecordCallback callback : RecordHandle.this.mCallbackArray) {
                callback.onComplete();
            }
        }

        @Override
        public void onReceiver(byte[] data) {
            for (IRecordCallback callback : RecordHandle.this.mCallbackArray) {
                callback.onReceiver(data);
            }
        }

        @Override
        public void onActionResult(int errCode) {
            for (IRecordCallback callback : RecordHandle.this.mCallbackArray) {
                callback.onActionResult(errCode);
            }
        }
    };
    private int mPackageLength;
    private static RecordHandle mInstance;
    private OnGattEventCallback callback = new OnGattEventCallback(){

        @Override
        public void onReceivedData(String uuid, byte[] data) {
            if (data != null) {
                if ((data[0] & 0xFF) == 188 && data[1] == -126) {
                    RecordHandle.this.mTotalCount = DataTransferUtils.bytesToShort(data, 2);
                    RecordHandle.this.mReceivedCount = data.length - 6;
                    RecordHandle.this.mReceiving = RecordHandle.this.mReceivedCount < RecordHandle.this.mTotalCount;
                    RecordHandle.access$402(RecordHandle.this, new byte[RecordHandle.this.mTotalCount]);
                    System.arraycopy(data, 6, RecordHandle.this.mDetails, 0, RecordHandle.this.mReceivedCount);
                    if (!RecordHandle.this.mReceiving) {
                        RecordHandle.this.parseRecord(RecordHandle.this.mDetails);
                    }
                } else if (RecordHandle.this.mReceiving) {
                    System.arraycopy(data, 0, RecordHandle.this.mDetails, RecordHandle.this.mReceivedCount, data.length);
                    RecordHandle.this.mReceivedCount += data.length;
                    RecordHandle.this.mReceiving = RecordHandle.this.mReceivedCount < RecordHandle.this.mTotalCount;
                    if (!RecordHandle.this.mReceiving) {
                        RecordHandle.this.parseRecord(RecordHandle.this.mDetails);
                    }
                } else if ((data[0] & 0xFF) == 188 && data[1] == -128) {
                    RecordHandle.this.mTotalCountName = DataTransferUtils.bytesToShort(data, 2);
                    RecordHandle.this.mReceivedCountName = data.length - 6;
                    RecordHandle.this.mReceivingName = RecordHandle.this.mReceivedCountName < RecordHandle.this.mTotalCountName;
                    RecordHandle.access$902(RecordHandle.this, new byte[RecordHandle.this.mTotalCountName]);
                    System.arraycopy(data, 6, RecordHandle.this.mDetailsName, 0, RecordHandle.this.mReceivedCountName);
                    if (!RecordHandle.this.mReceivingName) {
                        int total = ByteUtil.bytesToInt(Arrays.copyOfRange(RecordHandle.this.mDetailsName, 1, 2));
                        int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(RecordHandle.this.mDetailsName, 2, 3));
                        if (currIndex == 0) {
                            RecordHandle.this.fileNames = new ArrayList();
                        }
                        if (total > 0) {
                            int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(RecordHandle.this.mDetailsName, 8, 9));
                            RecordHandle.this.parseRecordData(RecordHandle.this.mDetailsName, 4, 9, offset, total, 0);
                        } else {
                            RecordHandle.this.mCallback.onFileNames(RecordHandle.this.fileNames);
                        }
                    }
                } else if (RecordHandle.this.mReceivingName) {
                    System.arraycopy(data, 0, RecordHandle.this.mDetailsName, RecordHandle.this.mReceivedCountName, data.length);
                    RecordHandle.this.mReceivedCountName += data.length;
                    RecordHandle.this.mReceivingName = RecordHandle.this.mReceivedCountName < RecordHandle.this.mTotalCountName;
                    if (!RecordHandle.this.mReceivingName) {
                        Log.i((String)RecordHandle.TAG, (String)ByteUtil.byteArrayToString(RecordHandle.this.mDetailsName));
                        int total = ByteUtil.bytesToInt(Arrays.copyOfRange(RecordHandle.this.mDetailsName, 1, 2));
                        int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(RecordHandle.this.mDetailsName, 2, 3));
                        if (currIndex == 0) {
                            RecordHandle.this.fileNames = new ArrayList();
                        }
                        if (total > 0) {
                            int offset = ByteUtil.bytesToInt(Arrays.copyOfRange(RecordHandle.this.mDetailsName, 8, 9));
                            RecordHandle.this.parseRecordData(RecordHandle.this.mDetailsName, 4, 9, offset, total, 0);
                        } else {
                            RecordHandle.this.mCallback.onFileNames(RecordHandle.this.fileNames);
                        }
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
    public static RecordHandle getInstance() {
        if (mInstance != null) return mInstance;
        Class<RecordHandle> clazz = RecordHandle.class;
        synchronized (RecordHandle.class) {
            if (mInstance != null) return mInstance;
            mInstance = new RecordHandle();
            // ** MonitorExit[var0] (shouldn't be in output)
            return mInstance;
        }
    }

    private RecordHandle() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
        Log.i((String)TAG, (String)("create FileHandle.. mPackageLength: " + this.mPackageLength));
    }

    public void initRegister() {
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    public void setDeviceOperateManagerCallback() {
        BleOperateManager.getInstance().setCallback(this.callback);
    }

    private void parseRecord(byte[] data) {
        int total = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 0, 4));
        int curr = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 4, 8));
        Log.i((String)TAG, (String)(total + "---" + curr));
        try {
            float percent = (float)curr * 1.0f * 100.0f / (float)total;
            DecimalFormat df = new DecimalFormat("#.00", new DecimalFormatSymbols(Locale.US));
            percent = Float.parseFloat(df.format(percent));
            this.mCallback.onProgress(Math.min(percent, 100.0f));
            if (curr < total) {
                this.mCallback.onReceiver(Arrays.copyOfRange(data, 8, data.length));
                this.readRecordFile(curr + 1, this.fileName);
            } else {
                this.mCallback.onComplete();
            }
        }
        catch (NumberFormatException e) {
            e.printStackTrace();
        }
    }

    private void parseRecordData(byte[] data, int lengthStart, int start, int offset, int total, int currIndex) {
        try {
            ++currIndex;
            int fileLength = ByteUtil.bytesToInt(Arrays.copyOfRange(data, lengthStart, lengthStart + 4));
            byte[] nameArrays = Arrays.copyOfRange(data, start, start + offset);
            String name = this.unicodeByteToStr(nameArrays);
            RecordEntity entity = new RecordEntity();
            entity.setFileName(name);
            entity.setLength(fileLength);
            this.fileNames.add(entity);
            if (currIndex < total) {
                this.parseRecordData(data, start + offset, start + 4 + offset + 1, data[start + 4 + offset], total, currIndex);
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

    public void registerCallback(IRecordCallback callback) {
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
        byte[] data = new byte[]{3, (byte)packIndex};
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(128, data), this.mPackageLength));
    }

    public void readRecordFile(int packageIndex, String fileName) {
        this.resetPackageLength();
        this.fileName = fileName;
        byte[] indexArray = ByteUtil.intToByte(packageIndex, 4);
        byte[] deleteBytes = fileName.getBytes(Charset.forName("unicode"));
        if (!ByteUtil.byteArrayToString(deleteBytes).startsWith("fffe")) {
            for (int i = 0; i < deleteBytes.length; i += 2) {
                int index = ByteUtil.bytesToInt(Arrays.copyOfRange(deleteBytes, i, i + 2));
                deleteBytes[i] = (byte)ByteUtil.hiword(index);
                deleteBytes[i + 1] = (byte)ByteUtil.loword(index);
            }
        }
        byte[] data = new byte[]{indexArray[0], indexArray[1], indexArray[2], indexArray[3], (byte)deleteBytes.length};
        byte[] b = ByteUtil.concat(data, deleteBytes);
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(130, b), this.mPackageLength));
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

    static /* synthetic */ byte[] access$402(RecordHandle x0, byte[] x1) {
        x0.mDetails = x1;
        return x1;
    }

    static /* synthetic */ byte[] access$902(RecordHandle x0, byte[] x1) {
        x0.mDetailsName = x1;
        return x1;
    }
}

