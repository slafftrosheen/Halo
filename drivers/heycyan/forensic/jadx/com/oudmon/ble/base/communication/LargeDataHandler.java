/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication;

import android.util.Log;
import com.oudmon.ble.base.bluetooth.BleOperateManager;
import com.oudmon.ble.base.bluetooth.queue.BleDataBean;
import com.oudmon.ble.base.bluetooth.queue.BleThreadManager;
import com.oudmon.ble.base.communication.ILargeDataImageResponse;
import com.oudmon.ble.base.communication.ILargeDataResponse;
import com.oudmon.ble.base.communication.JPackageManager;
import com.oudmon.ble.base.communication.bigData.bean.GlassModelControl;
import com.oudmon.ble.base.communication.bigData.bean.SyncTime;
import com.oudmon.ble.base.communication.bigData.bean.WifiInfoReq;
import com.oudmon.ble.base.communication.bigData.resp.AiChatResponse;
import com.oudmon.ble.base.communication.bigData.resp.BatteryResponse;
import com.oudmon.ble.base.communication.bigData.resp.ClassBluetoothResponse;
import com.oudmon.ble.base.communication.bigData.resp.DeviceInfoResponse;
import com.oudmon.ble.base.communication.bigData.resp.GlassModelControlResponse;
import com.oudmon.ble.base.communication.bigData.resp.GlassesAiVoicePlayStatusRsp;
import com.oudmon.ble.base.communication.bigData.resp.GlassesAiVoiceRsp;
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener;
import com.oudmon.ble.base.communication.bigData.resp.GlassesTouchSupportRsp;
import com.oudmon.ble.base.communication.bigData.resp.GlassesWearRsp;
import com.oudmon.ble.base.communication.bigData.resp.PictureThumbnailsResponse;
import com.oudmon.ble.base.communication.bigData.resp.SyncTimeResponse;
import com.oudmon.ble.base.communication.bigData.resp.VolumeControlResponse;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import com.oudmon.ble.base.communication.utils.CRC16;
import com.oudmon.ble.base.request.EnableNotifyRequest;
import com.oudmon.ble.base.request.WriteRequest;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LargeDataHandler {
    public static final byte ACTION_DEVICE_DATA_REPORTING = 115;
    public static final byte ACTION_SYNC_TIME = 64;
    public static final byte ACTION_GLASSES_CONTROL = 65;
    public static final byte ACTION_GLASSES_BATTERY = 66;
    public static final byte ACTION_DEVICE_INFO = 67;
    public static final byte ACTION_DEVICE_AI_VOICE = 68;
    public static final byte ACTION_DEVICE_HEART_BEAT = 69;
    public static final byte ACTION_DEVICE_WEAR = 70;
    public static final byte ACTION_DEVICE_WEAR_SUPPORT = 71;
    public static final byte ACTION_BT_MAC_Protocol = 46;
    public static final byte ACTION_OTA_SOC = -4;
    public static final byte ACTION_GPT_UPLOAD = 89;
    public static final byte ACTION_PICTURE_THUMBNAILS = -3;
    public static final byte ACTION_VOICE_STATUS = 72;
    public static final byte ACTION_BT_CONNECT = 73;
    public static final byte ACTION_VOLUME_CONTROL = 81;
    public static final byte ACTION_SPEAK_SOUND_SWITCH = 82;
    private int mPackageLength;
    private GlassesDeviceNotifyListener deviceNotifyListener = new GlassesDeviceNotifyListener();
    private ConcurrentHashMap<String, ILargeDataResponse<BatteryResponse>> batterySparseArray = new ConcurrentHashMap();
    private static final UUID SERIAL_PORT_SERVICE = UUID.fromString("de5bf728-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_NOTIFY = UUID.fromString("de5bf729-d711-4e47-af26-65e3012a5dc7");
    private static final UUID SERIAL_PORT_CHARACTER_WRITE = UUID.fromString("de5bf72a-d711-4e47-af26-65e3012a5dc7");
    private EnableNotifyRequest enableNotifyRequest = new EnableNotifyRequest(SERIAL_PORT_SERVICE, SERIAL_PORT_CHARACTER_NOTIFY, new EnableNotifyRequest.ListenerCallback(){

        @Override
        public void enable(boolean result) {
            if (!result) {
                LargeDataHandler.this.initEnable();
            }
        }
    });
    private ConcurrentHashMap<Integer, ILargeDataResponse> respMap = new ConcurrentHashMap();
    private ConcurrentHashMap<Integer, ILargeDataResponse> noClearMap = new ConcurrentHashMap();
    private static LargeDataHandler mInstance;

    private LargeDataHandler() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
        this.noClearMap.put(115, this.deviceNotifyListener);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static LargeDataHandler getInstance() {
        if (mInstance != null) return mInstance;
        Class<LargeDataHandler> clazz = LargeDataHandler.class;
        synchronized (LargeDataHandler.class) {
            if (mInstance != null) return mInstance;
            mInstance = new LargeDataHandler();
            // ** MonitorExit[var0] (shouldn't be in output)
            return mInstance;
        }
    }

    public void initEnable() {
        this.enableNotifyRequest.setEnable(true);
        BleOperateManager.getInstance().execute(this.enableNotifyRequest);
        this.noClearMap.put(115, this.deviceNotifyListener);
    }

    public void disEnable() {
        this.enableNotifyRequest.setEnable(false);
        BleOperateManager.getInstance().execute(this.enableNotifyRequest);
    }

    public void packageLength() {
        this.mPackageLength = JPackageManager.getInstance().getLength();
    }

    public void initPackageNotify(ILargeDataResponse<AiChatResponse> listener) {
        this.respMap.put(89, listener);
    }

    public void removeGptNotify() {
        this.respMap.remove(89);
    }

    public void addOutDeviceListener(int type, ILargeDataResponse outRspIOdmOpResponse) {
        this.deviceNotifyListener.setOutRspIOdmOpResponse(type, outRspIOdmOpResponse);
    }

    public void removeOutDeviceListener(int key) {
        this.deviceNotifyListener.removeCallback(key);
    }

    public void syncTime(ILargeDataResponse<SyncTimeResponse> response) {
        this.respMap.put(64, response);
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(64, new SyncTime(1).getSubData()), this.mPackageLength));
    }

    public void addBatteryCallBack(String key, ILargeDataResponse<BatteryResponse> listener) {
        this.batterySparseArray.put(key, listener);
    }

    public void removeBatteryCallBack(String key) {
        this.batterySparseArray.remove(key);
    }

    public void syncBattery() {
        this.respMap.put(66, new ILargeDataResponse<BatteryResponse>(){

            @Override
            public void parseData(int cmdType, BatteryResponse response) {
                for (ILargeDataResponse outRspIOdmOpResponse : LargeDataHandler.this.batterySparseArray.values()) {
                    outRspIOdmOpResponse.parseData(cmdType, response);
                }
            }
        });
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(66, new byte[2]), this.mPackageLength, 200));
    }

    public void openBT() {
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(73, new byte[]{2, 1}), this.mPackageLength));
    }

    public void setVolumeControl(int minMusic, int maxMusic, int currMusic, int minCall, int maxCall, int currCall, int systemMin, int systemMax, int systemCurr, int currVolumeType) {
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(81, new byte[]{2, 1, (byte)minMusic, (byte)maxMusic, (byte)currMusic, 2, (byte)minCall, (byte)maxCall, (byte)currCall, 3, (byte)systemMin, (byte)systemMax, (byte)systemCurr, (byte)currVolumeType}), this.mPackageLength));
    }

    public void getVolumeControl(ILargeDataResponse<VolumeControlResponse> response) {
        this.respMap.put(81, response);
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(81, new byte[]{1}), this.mPackageLength));
    }

    public void writeIpToSoc(String url, ILargeDataResponse<BatteryResponse> response) {
        this.respMap.put(-4, response);
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(-4, new WifiInfoReq(url).getSubData()), this.mPackageLength));
    }

    public void syncDeviceInfo(ILargeDataResponse<DeviceInfoResponse> response) {
        this.respMap.put(67, response);
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(67, new byte[2]), this.mPackageLength, 200));
    }

    public void aiVoiceWake(boolean write, boolean isOpen, ILargeDataResponse<GlassesAiVoiceRsp> response) {
        this.respMap.put(68, response);
        if (write) {
            byte[] data = new byte[]{2, (byte)(isOpen ? 1 : 0)};
            BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(68, data), this.mPackageLength));
        } else {
            BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(68, new byte[]{1, 0}), this.mPackageLength));
        }
    }

    public void aiVoicePlay(int status, ILargeDataResponse<GlassesAiVoicePlayStatusRsp> response) {
        byte[] data = new byte[]{2, (byte)status};
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(72, data), this.mPackageLength));
    }

    public void wearCheck(boolean write, boolean isOpen, ILargeDataResponse<GlassesWearRsp> response) {
        this.respMap.put(70, response);
        if (write) {
            byte[] data = new byte[]{2, (byte)(isOpen ? 1 : 0)};
            BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(70, data), this.mPackageLength));
        } else {
            BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(70, new byte[]{1, 0}), this.mPackageLength));
        }
    }

    public void wearFunctionSupport(ILargeDataResponse<GlassesTouchSupportRsp> response) {
        this.respMap.put(71, response);
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(71, new byte[]{1, 0}), this.mPackageLength));
    }

    public void glassesControl(byte[] sendData, ILargeDataResponse<GlassModelControlResponse> response) {
        this.respMap.put(65, response);
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(65, new GlassModelControl(sendData).getSubData()), this.mPackageLength));
    }

    public void speakSoundSwitch(boolean phone) {
        byte[] data = new byte[]{2, phone ? (byte)2 : 1};
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(82, data), this.mPackageLength));
    }

    public void removeGlassesControlCallback() {
        this.respMap.remove(65);
    }

    public void getPictureThumbnails(final ILargeDataImageResponse listener) {
        this.respMap.put(-3, new ILargeDataResponse<PictureThumbnailsResponse>(){

            @Override
            public void parseData(int cmdType, PictureThumbnailsResponse response) {
                Log.i((String)"GLASSES_LOG", (String)ByteUtil.byteArrayToString(response.getSubData()));
                if ((cmdType & 0xFF) == 253) {
                    int total = ByteUtil.bytesToInt(Arrays.copyOfRange(response.getSubData(), 7, 9));
                    int currIndex = ByteUtil.bytesToInt(Arrays.copyOfRange(response.getSubData(), 9, 11));
                    Log.i((String)"GLASSES_LOG", (String)(currIndex + "--" + total));
                    if (total <= 0) {
                        return;
                    }
                    if (currIndex + 1 == total) {
                        listener.parseData(cmdType, true, Arrays.copyOfRange(response.getSubData(), 11, response.getSubData().length));
                    } else {
                        LargeDataHandler.this.syncPictureThumbnails(++currIndex);
                        listener.parseData(cmdType, false, Arrays.copyOfRange(response.getSubData(), 11, response.getSubData().length));
                    }
                }
            }
        });
        this.syncPictureThumbnails(0);
    }

    private void syncPictureThumbnails(int index) {
        byte[] cmdData = new byte[]{1, (byte)ByteUtil.loword(index), (byte)ByteUtil.hiword(index)};
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(-3, cmdData), this.mPackageLength));
    }

    public void syncHeartBeat(int type) {
        byte[] cmdData = new byte[]{(byte)type, 1};
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(69, cmdData), this.mPackageLength));
    }

    public void syncClassicBluetooth(ILargeDataResponse<ClassBluetoothResponse> response) {
        this.respMap.put(46, response);
        byte[] cmdData = new byte[1];
        BleThreadManager.getInstance().addData(new BleDataBean(this.addHeader(46, cmdData), this.mPackageLength));
    }

    public ConcurrentHashMap<Integer, ILargeDataResponse> getRespMap() {
        return this.respMap;
    }

    public ConcurrentHashMap<Integer, ILargeDataResponse> getNoClearMap() {
        if (this.noClearMap.isEmpty()) {
            this.noClearMap.put(115, this.deviceNotifyListener);
        }
        return this.noClearMap;
    }

    public void cleanMap() {
        this.respMap.clear();
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
        Log.i((String)"GLASSES_LOG", (String)("getWriteRequest: data=" + DataTransferUtils.getHexString(data)));
        WriteRequest noRspInstance = WriteRequest.getNoRspInstance(SERIAL_PORT_SERVICE, SERIAL_PORT_CHARACTER_WRITE);
        noRspInstance.setValue(data);
        return noRspInstance;
    }
}

