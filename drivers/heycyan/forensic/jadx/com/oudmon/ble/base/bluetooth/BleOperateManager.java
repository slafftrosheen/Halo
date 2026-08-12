/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.SuppressLint
 *  android.app.Application
 *  android.bluetooth.BluetoothAdapter
 *  android.bluetooth.BluetoothDevice
 *  android.bluetooth.BluetoothGatt
 *  android.bluetooth.BluetoothGattCharacteristic
 *  android.bluetooth.BluetoothGattDescriptor
 *  android.content.BroadcastReceiver
 *  android.content.Context
 *  android.content.Intent
 *  android.content.IntentFilter
 *  android.os.Handler
 *  android.os.HandlerThread
 *  android.os.Looper
 *  android.os.Parcelable
 *  android.text.TextUtils
 *  android.util.Log
 *  android.util.SparseArray
 *  androidx.localbroadcastmanager.content.LocalBroadcastManager
 */
package com.oudmon.ble.base.bluetooth;

import android.annotation.SuppressLint;
import android.app.Application;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcelable;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.oudmon.ble.base.bluetooth.BleAction;
import com.oudmon.ble.base.bluetooth.BleBaseControl;
import com.oudmon.ble.base.bluetooth.IBleListener;
import com.oudmon.ble.base.bluetooth.OnGattEventCallback;
import com.oudmon.ble.base.bluetooth.QCBluetoothCallbackBigDataCloneReceiver;
import com.oudmon.ble.base.bluetooth.QCBluetoothCallbackCloneReceiver;
import com.oudmon.ble.base.bluetooth.QCBluetoothCallbackReceiver;
import com.oudmon.ble.base.communication.CommandHandle;
import com.oudmon.ble.base.communication.Constants;
import com.oudmon.ble.base.communication.ICommandResponse;
import com.oudmon.ble.base.communication.responseImpl.DeviceNotifyListener;
import com.oudmon.ble.base.communication.responseImpl.DeviceSportNotifyListener;
import com.oudmon.ble.base.communication.responseImpl.InnerCameraNotifyListener;
import com.oudmon.ble.base.communication.responseImpl.PackageLengthListener;
import com.oudmon.ble.base.request.BaseRequest;
import com.oudmon.ble.base.request.EnableNotifyRequest;
import com.oudmon.ble.base.request.LocalWriteRequest;
import com.oudmon.qc_utils.bluetooth.BluetoothUtils;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BleOperateManager
extends HandlerThread
implements IBleListener {
    private static final String TAG = "DeviceOperateManager";
    private static BleOperateManager DeviceOperateManager = null;
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());
    private Handler workThreadHandler;
    private final Object mLock = new Object();
    private boolean mRequestCompleted = false;
    private final Context mContext;
    private ConcurrentHashMap<Integer, LocalWriteRequest> localWriteRequestConcurrentHashMap = new ConcurrentHashMap();
    private SparseArray<ICommandResponse> notifySparseArray = new SparseArray();
    private InnerCameraNotifyListener innerCameraNotifyListener;
    private OnGattEventCallback callback;
    private String reConnectMac;
    private DeviceNotifyListener deviceNotifyListener = new DeviceNotifyListener();
    private Application application;
    private FirmwareRunnable timeoutFirmwareRunnable = new FirmwareRunnable();
    private DeviceSportNotifyListener deviceSportNotifyListener = new DeviceSportNotifyListener();
    private boolean ready;
    Runnable runnable = new Runnable(){

        @Override
        public void run() {
            Log.e((String)BleOperateManager.TAG, (String)"---lock timeout---");
            BleOperateManager.this.mRequestCompleted = true;
            BleOperateManager.this.notifyLock();
        }
    };
    Runnable runnableEnable = new Runnable(){

        @Override
        public void run() {
            BleOperateManager.this.mRequestCompleted = true;
            BleOperateManager.this.notifyLock();
            EnableNotifyRequest enableNotifyRequest = new EnableNotifyRequest(Constants.UUID_SERVICE, Constants.UUID_READ, new EnableNotifyRequest.ListenerCallback(){

                @Override
                public void enable(boolean result) {
                    Log.e((String)BleOperateManager.TAG, (String)("enableUUID:" + result));
                    BleOperateManager.this.mainThreadHandler.removeCallbacks(BleOperateManager.this.runnableEnable);
                }
            });
            enableNotifyRequest.setEnable(true);
            DeviceOperateManager;
            BleOperateManager.getInstance().execute(enableNotifyRequest);
        }
    };

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static BleOperateManager getInstance(Application application) {
        if (DeviceOperateManager != null) return DeviceOperateManager;
        Class<BleOperateManager> clazz = BleOperateManager.class;
        synchronized (BleOperateManager.class) {
            if (DeviceOperateManager != null) return DeviceOperateManager;
            DeviceOperateManager = new BleOperateManager((Context)application);
            // ** MonitorExit[var1_1] (shouldn't be in output)
            return DeviceOperateManager;
        }
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public String getReConnectMac() {
        return this.reConnectMac;
    }

    public void setReConnectMac(String reConnectMac) {
        this.reConnectMac = reConnectMac;
    }

    public void setCallback(OnGattEventCallback callback) {
        this.callback = callback;
    }

    public static BleOperateManager getInstance() {
        return DeviceOperateManager;
    }

    private BleOperateManager(Context context) {
        super(TAG);
        this.mContext = context;
        BleBaseControl.getInstance(context).setListener(this);
        this.notifySparseArray.put(47, (Object)new PackageLengthListener());
        this.start();
        this.workThreadHandler = new Handler(this.getLooper());
    }

    public ConcurrentHashMap<Integer, LocalWriteRequest> getLocalWriteRequestConcurrentHashMap() {
        return this.localWriteRequestConcurrentHashMap;
    }

    public void setBluetoothTurnOff(boolean onOrOff) {
        BleBaseControl.getInstance().setBluetoothTurnOff(onOrOff);
    }

    public void addOutCameraListener(ICommandResponse outRspIOdmOpResponse) {
        this.innerCameraNotifyListener.setOutRspIOdmOpResponse(outRspIOdmOpResponse);
    }

    public void removeOutCameraListener() {
        this.innerCameraNotifyListener.setOutRspIOdmOpResponse(null);
    }

    public void addSportDeviceListener(int type, ICommandResponse outRspIOdmOpResponse) {
        this.deviceSportNotifyListener.setOutRspIOdmOpResponse(type, outRspIOdmOpResponse);
    }

    public void removeSportDeviceListener(int key) {
        this.deviceSportNotifyListener.removeCallback(key);
    }

    public void addOutDeviceListener(int type, ICommandResponse outRspIOdmOpResponse) {
        this.deviceNotifyListener.setOutRspIOdmOpResponse(type, outRspIOdmOpResponse);
    }

    public void removeOutDeviceListener(int key) {
        this.deviceNotifyListener.removeCallback(key);
    }

    public void removeOthersListener() {
        this.deviceNotifyListener.removeOtherCallbacks();
    }

    public boolean addNotifyListener(int key, ICommandResponse iOpResponse) {
        if (iOpResponse == null) {
            return false;
        }
        this.notifySparseArray.put(key, (Object)iOpResponse);
        return true;
    }

    public void removeNotifyListener(int key) {
        this.notifySparseArray.delete(key);
    }

    public SparseArray<ICommandResponse> getNotifySparseArray() {
        return this.notifySparseArray;
    }

    public void unBindDevice() {
        this.setNeedConnect(false);
        this.disconnect();
        this.setMacNull();
    }

    public void setMacNull() {
        BleBaseControl.getInstance().setmDeviceAddress(null);
    }

    public void connectDirectly(String macAddress) {
        BleBaseControl.getInstance().connect(macAddress);
    }

    public void connectWithScan(String macAddress) {
        if (TextUtils.isEmpty((CharSequence)macAddress)) {
            return;
        }
        if (TextUtils.isEmpty((CharSequence)this.reConnectMac)) {
            return;
        }
        BleBaseControl.getInstance().setNeedReconnect(true);
        BleBaseControl.getInstance().setmDeviceAddress(macAddress);
        BleBaseControl.getInstance().reconnectOpeningUp();
    }

    public void disconnect() {
        this.ready = false;
        Log.e((String)TAG, (String)"disconnect...");
        BleBaseControl.getInstance().disconnectDevice(BleBaseControl.getInstance().getmDeviceAddress());
    }

    public void setNeedConnect(boolean needConnect) {
        BleBaseControl.getInstance().setNeedReconnect(needConnect);
    }

    @Override
    public boolean execute(final BaseRequest baseCharAction) {
        if (!BluetoothUtils.isEnabledBluetooth(this.mContext)) {
            Log.e((String)TAG, (String)"connectDirectly: \u84dd\u7259\u672a\u6253\u5f00");
            return false;
        }
        if (!BleBaseControl.getInstance().ismIsConnected()) {
            return false;
        }
        if (baseCharAction.writeRequest && !this.ready) {
            return false;
        }
        this.workThreadHandler.postDelayed(this.runnable, 5000L);
        this.workThreadHandler.post(new Runnable(){

            /*
             * WARNING - Removed try catching itself - possible behaviour change.
             */
            @Override
            public void run() {
                Object object = BleOperateManager.this.mLock;
                synchronized (object) {
                    try {
                        if (baseCharAction.needInitCharacteristic()) {
                            BluetoothGattCharacteristic charCharacteristic = BleBaseControl.getInstance().findTheGattCharacteristic(baseCharAction.getServiceUuid(), baseCharAction.getCharUuid());
                            if (charCharacteristic == null) {
                                return;
                            }
                            boolean result = baseCharAction.execute(BleBaseControl.getInstance().getGatt(BleBaseControl.getInstance().getmDeviceAddress()), charCharacteristic);
                            if (!result) {
                                BleOperateManager.this.notifyLock();
                            }
                        } else {
                            boolean result = baseCharAction.execute(BleBaseControl.getInstance().getGatt(BleBaseControl.getInstance().getmDeviceAddress()), null);
                            Log.e((String)BleOperateManager.TAG, (String)(result + ""));
                            if (!result) {
                                BleOperateManager.this.notifyLock();
                            }
                        }
                        BleOperateManager.this.mRequestCompleted = false;
                        if (BleBaseControl.getInstance().ismIsConnected()) {
                            BleOperateManager.this.waitUntilActionResponse();
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        return true;
    }

    private void waitUntilActionResponse() {
        try {
            while (!this.mRequestCompleted) {
                this.mLock.wait();
            }
        }
        catch (Exception e) {
            Log.e((String)TAG, (String)"Sleeping interrupted", (Throwable)e);
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    protected void notifyLock() {
        this.workThreadHandler.removeCallbacks(this.runnable);
        Object object = this.mLock;
        synchronized (object) {
            this.mRequestCompleted = true;
            this.mLock.notifyAll();
        }
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        this.notifyLock();
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
        this.notifyLock();
    }

    @Override
    public boolean isConnected() {
        boolean connect = BleBaseControl.getInstance().ismIsConnected();
        if (!connect) {
            this.ready = false;
        }
        return connect;
    }

    public boolean isReady() {
        return this.ready;
    }

    public void setReady(boolean ready) {
        this.mainThreadHandler.removeCallbacks((Runnable)this.timeoutFirmwareRunnable);
        if (!this.ready) {
            this.mainThreadHandler.postDelayed(new Runnable(){

                @Override
                public void run() {
                    Intent intent = new Intent("com.swatchdevice.pro.sdk.ble.service_discovered");
                    intent.putExtra("ADDRESS", BleBaseControl.getInstance().getmDeviceAddress());
                    BleOperateManager.this.mySendBroadcast(intent);
                }
            }, 2500L);
            this.ready = ready;
        } else {
            Log.e((String)TAG, (String)"\u5df2\u7ecf\u66f4\u65b0\u8fc7\u7248\u672c\u53f7");
        }
    }

    @Override
    public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
        this.notifyLock();
    }

    @Override
    public void startConnect() {
        Intent intent = new Intent("com.swatchdevice.pro.sdk.ble.start_connect");
        this.mySendBroadcast(intent);
    }

    @Override
    public void bleGattConnected(BluetoothDevice device) {
        Intent intent = new Intent("com.swatchdevice.pro.sdk.ble.gatt_connected");
        intent.putExtra("DEVICE", (Parcelable)device);
        intent.putExtra("ADDRESS", device.getAddress());
        this.mySendBroadcast(intent);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    @Override
    public void bleGattDisconnect(BluetoothDevice device) {
        Intent intent = new Intent("com.swatchdevice.pro.sdk.ble.gatt_disconnected");
        intent.putExtra("DEVICE", (Parcelable)device);
        intent.putExtra("ADDRESS", device.getAddress());
        this.mySendBroadcast(intent);
        Object object = this.mLock;
        synchronized (object) {
            this.localWriteRequestConcurrentHashMap.clear();
            this.notifyLock();
        }
    }

    @Override
    public void bleServiceDiscovered(int state, final String address) {
        Log.e((String)TAG, (String)"---------bleServiceDiscovered address");
        this.enableUUID();
        this.mainThreadHandler.postDelayed(new Runnable(){

            @Override
            public void run() {
                BleOperateManager.this.runCommonCmd();
            }
        }, 500L);
        this.mainThreadHandler.postDelayed((Runnable)this.timeoutFirmwareRunnable, 1000L);
        this.mainThreadHandler.postDelayed((Runnable)this.timeoutFirmwareRunnable, 1500L);
        this.mainThreadHandler.postDelayed(new Runnable(){

            @Override
            public void run() {
                Intent intent = new Intent("com.swatchdevice.pro.sdk.ble.service_discovered");
                intent.putExtra("ADDRESS", address);
                BleOperateManager.this.mySendBroadcast(intent);
            }
        }, 1000L);
    }

    @Override
    public void bleStatus(int status, int newState) {
        Intent intent = new Intent("com.swatchdevice.pro.sdk.ble.BLE_STATUS");
        intent.putExtra("EXTRA_STATUS", status);
        intent.putExtra("EXTRA_BLE_NEW_STATE", newState);
        this.mySendBroadcast(intent);
    }

    @Override
    public void bleCharacteristicRead(String address, String uuid, int status, byte[] value) {
        Intent intent = new Intent("com.swatchdevice.pro.sdk.ble.characteristic_read");
        intent.putExtra("ADDRESS", address);
        intent.putExtra("CHARACTER_UUID", uuid);
        intent.putExtra("STATUS", status);
        intent.putExtra("VALUE", value);
        this.mySendBroadcast(intent);
        this.notifyLock();
    }

    @Override
    public void bleCharacteristicNotification() {
        Intent intent = new Intent("com.swatchdevice.pro.sdk.ble.characteristic_notification_qc");
        this.mySendBroadcast(intent);
        this.notifyLock();
    }

    @Override
    public void bleCharacteristicWrite(String address, String uuid, int status, byte[] data) {
        Intent intent = new Intent("com.swatchdevice.pro.characteristic_write_qc");
        intent.putExtra("ADDRESS", address);
        intent.putExtra("CHARACTER_UUID", uuid);
        intent.putExtra("STATUS", status);
        intent.putExtra("DATA", data);
        this.mySendBroadcast(intent);
        this.notifyLock();
    }

    @Override
    public void bleCharacteristicChanged(String address, String uuid, byte[] value) {
        Intent intent = new Intent("com.swatchdevice.pro.characteristic_changed_qc");
        intent.putExtra("ADDRESS", address);
        intent.putExtra("CHARACTER_UUID", uuid);
        intent.putExtra("VALUE", value);
        this.mySendBroadcast(intent);
        if (this.callback != null) {
            this.callback.onReceivedData(uuid, value);
        }
    }

    @Override
    public void bleNoCallback() {
        Intent intent = new Intent("com.swatchdevice.pro.sdk.ble.BLE_NO_CALLBACK");
        this.mySendBroadcast(intent);
    }

    private void mySendBroadcast(Intent intent) {
        LocalBroadcastManager.getInstance((Context)this.mContext).sendBroadcast(intent);
    }

    public void enableUUID() {
        this.mainThreadHandler.removeCallbacks(this.runnableEnable);
        this.mainThreadHandler.postDelayed(this.runnableEnable, 4000L);
        EnableNotifyRequest enableNotifyRequest = new EnableNotifyRequest(Constants.UUID_SERVICE, Constants.UUID_READ, new EnableNotifyRequest.ListenerCallback(){

            @Override
            public void enable(boolean result) {
                Log.e((String)BleOperateManager.TAG, (String)("enableUUID:" + result));
                BleOperateManager.this.mainThreadHandler.removeCallbacks(BleOperateManager.this.runnableEnable);
            }
        });
        enableNotifyRequest.setEnable(true);
        DeviceOperateManager.getInstance().execute(enableNotifyRequest);
    }

    public void init() {
        IntentFilter intentFilter = BleAction.getIntentFilter();
        QCBluetoothCallbackReceiver bluetoothDataParseReceiver = new QCBluetoothCallbackReceiver();
        LocalBroadcastManager.getInstance((Context)this.mContext).registerReceiver((BroadcastReceiver)bluetoothDataParseReceiver, intentFilter);
        QCBluetoothCallbackCloneReceiver cloneReceiver = new QCBluetoothCallbackCloneReceiver();
        LocalBroadcastManager.getInstance((Context)this.mContext).registerReceiver((BroadcastReceiver)cloneReceiver, intentFilter);
        QCBluetoothCallbackBigDataCloneReceiver bigDataClone = new QCBluetoothCallbackBigDataCloneReceiver();
        LocalBroadcastManager.getInstance((Context)this.mContext).registerReceiver((BroadcastReceiver)bigDataClone, intentFilter);
    }

    private void runCommonCmd() {
        CommandHandle.getInstance().execReadCmd(CommandHandle.getInstance().getReadHwRequest());
        CommandHandle.getInstance().execReadCmd(CommandHandle.getInstance().getReadFmRequest());
    }

    public void classicBluetoothStartScan() {
        BleBaseControl.getInstance().classicBluetoothScan();
    }

    public void classicBluetoothStopScan() {
        BleBaseControl.getInstance().cancelScanBluetooth();
    }

    public void createBondBlueTooth(BluetoothDevice device) {
        BleBaseControl.getInstance().createBondBlueTooth(device);
    }

    public void createBondBluetoothJieLi(BluetoothDevice device) {
        BleBaseControl.getInstance().createBond(device, 1);
    }

    public void setRtkBindTag(Boolean rtkBindTag) {
        BleBaseControl.getInstance().setRtkBindTag(rtkBindTag);
    }

    public BluetoothDevice getMacSystemBond(String address) {
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        Set devices1 = adapter.getBondedDevices();
        if (devices1.size() > 0) {
            for (BluetoothDevice bluetoothDevice : devices1) {
                if (bluetoothDevice == null || bluetoothDevice.getName() == null || bluetoothDevice.getAddress() == null || TextUtils.isEmpty((CharSequence)address) || !address.equalsIgnoreCase(bluetoothDevice.getAddress())) continue;
                return bluetoothDevice;
            }
        }
        return null;
    }

    public void bleCreateBond() {
        BleBaseControl.getInstance().bleCreateBond();
    }

    @SuppressLint(value={"MissingPermission"})
    public void createBondBluetoothJieLiFailRepeat(BluetoothDevice device) {
        try {
            device.createBond();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class FirmwareRunnable
    implements Runnable {
        @Override
        public void run() {
            BleOperateManager.this.runCommonCmd();
        }
    }
}

