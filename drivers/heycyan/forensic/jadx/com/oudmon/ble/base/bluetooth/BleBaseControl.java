/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.SuppressLint
 *  android.bluetooth.BluetoothAdapter
 *  android.bluetooth.BluetoothDevice
 *  android.bluetooth.BluetoothGatt
 *  android.bluetooth.BluetoothGattCallback
 *  android.bluetooth.BluetoothGattCharacteristic
 *  android.bluetooth.BluetoothGattDescriptor
 *  android.bluetooth.BluetoothGattService
 *  android.bluetooth.BluetoothManager
 *  android.content.Context
 *  android.os.Build$VERSION
 *  android.os.Handler
 *  android.os.Looper
 *  android.text.TextUtils
 *  android.util.Log
 *  androidx.core.app.ActivityCompat
 */
package com.oudmon.ble.base.bluetooth;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import androidx.core.app.ActivityCompat;
import com.oudmon.ble.base.bluetooth.BleOperateManager;
import com.oudmon.ble.base.bluetooth.IBleListener;
import com.oudmon.ble.base.bluetooth.queue.BleThreadManager;
import com.oudmon.ble.base.communication.Constants;
import com.oudmon.ble.base.scan.BleScannerCompat;
import com.oudmon.ble.base.scan.BleScannerHelper;
import com.oudmon.ble.base.scan.OnTheScanResult;
import com.oudmon.ble.base.util.AppUtil;
import com.oudmon.qc_utils.bluetooth.BluetoothUtils;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class BleBaseControl {
    private static final String TAG = "GLASSES_LOG";
    private static BleBaseControl bleBaseControl;
    private static final int GATT_CLOSE_DELAY_MILLIS = 500;
    private Context mContext;
    private BluetoothAdapter mBluetoothAdapter;
    private final Object mLock = new Object();
    private String mDeviceAddress;
    private volatile boolean mIsConnected;
    private volatile boolean connecting;
    protected Map<String, BluetoothGatt> mBluetoothGatt = new HashMap<String, BluetoothGatt>();
    private boolean isNeedReconnect = true;
    private int maxReconnect = 10;
    private int maxFail = 6;
    private AtomicInteger count = new AtomicInteger(0);
    private AtomicInteger failCount = new AtomicInteger(0);
    private IBleListener listener;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean bluetoothTurnOff = false;
    private HashMap<UUID, BluetoothGattCharacteristic> cacheGattCharacteristicHashMap = new HashMap();
    private SystemProxyTimeoutRunnable systemProxyTimeoutRunnable = new SystemProxyTimeoutRunnable();
    private Boolean rtkBindTag = false;
    BluetoothDevice bleConnectDevice;
    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback(){

        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i((String)BleBaseControl.TAG, (String)("onConnectionStateChange-->status = [" + status + "], newState = [" + newState + "]"));
            if (BleBaseControl.this.listener != null) {
                BleBaseControl.this.listener.bleStatus(status, newState);
            }
            String address = gatt.getDevice().getAddress();
            BleBaseControl.this.bleConnectDevice = gatt.getDevice();
            BleBaseControl.this.mHandler.removeCallbacks(BleBaseControl.this.mDiscoverServiceTimeoutRunnable);
            BleBaseControl.this.mHandler.removeCallbacks(BleBaseControl.this.mTimeoutRunnable);
            BleThreadManager.getInstance().clean();
            if (status != 0) {
                BleBaseControl.this.notifyMyAll();
                BleBaseControl.this.mIsConnected = false;
                if (BleBaseControl.this.listener != null) {
                    BleBaseControl.this.listener.bleGattDisconnect(gatt.getDevice());
                }
                BleBaseControl.this.disconnectDevice(address);
                BleBaseControl.this.failCount.incrementAndGet();
                this.reconnectFromStateChangeNoAutoConnect();
                return;
            }
            if (newState == 2) {
                BleBaseControl.this.cacheGattCharacteristicHashMap.clear();
                BleBaseControl.this.mHandler.removeCallbacks(BleBaseControl.this.mReconnectRunnable);
                BleBaseControl.this.waitFor(500L);
                boolean discoverServices = gatt.discoverServices();
                if (discoverServices) {
                    BleBaseControl.this.mHandler.removeCallbacks(BleBaseControl.this.mDiscoverServiceTimeoutRunnable);
                    BleBaseControl.this.mHandler.postDelayed(BleBaseControl.this.mDiscoverServiceTimeoutRunnable, 40000L);
                } else {
                    BleBaseControl.this.waitFor(1000L);
                    boolean discoverServices1 = gatt.discoverServices();
                    Log.i((String)BleBaseControl.TAG, (String)("-------1---" + discoverServices1));
                    if (!discoverServices1) {
                        BleBaseControl.this.disconnectDevice(address);
                        return;
                    }
                }
                BleBaseControl.this.count.getAndSet(0);
                BleBaseControl.this.failCount.getAndSet(0);
                BleBaseControl.this.mIsConnected = true;
                BleBaseControl.this.bluetoothTurnOff = false;
                BleBaseControl.this.mHandler.removeCallbacks(BleBaseControl.this.mReconnectRunnable);
                if (BleBaseControl.this.listener != null) {
                    BleBaseControl.this.listener.bleGattConnected(gatt.getDevice());
                }
            } else if (newState == 0) {
                BleBaseControl.this.mIsConnected = false;
                BleBaseControl.this.notifyMyAll();
                BleBaseControl.this.disconnectDevice(address);
                BleBaseControl.this.reconnectDevice();
                if (BleBaseControl.this.listener != null) {
                    BleBaseControl.this.listener.bleGattDisconnect(gatt.getDevice());
                }
            }
        }

        public void disconnectDeviceNotClose(String address) {
            try {
                BluetoothGatt gatt;
                BleBaseControl.this.connecting = false;
                BleBaseControl.this.mIsConnected = false;
                Log.i((String)BleBaseControl.TAG, (String)(address + "  gatt map size:" + BleBaseControl.this.mBluetoothGatt.size()));
                if (BleBaseControl.this.mBluetoothGatt.containsKey(address) && (gatt = BleBaseControl.this.mBluetoothGatt.get(address)) != null) {
                    Log.i((String)BleBaseControl.TAG, (String)"gatt disconnect it");
                    gatt.disconnect();
                    BleBaseControl.this.mHandler.postDelayed(new Runnable(){

                        @Override
                        public void run() {
                            BleBaseControl.this.refreshDeviceCache(gatt);
                        }
                    }, 500L);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void reconnectFromStateChangeNoAutoConnect() {
            BleBaseControl.this.mHandler.postDelayed(new Runnable(){

                @Override
                public void run() {
                    BleBaseControl.this.reconnectDevice();
                }
            }, 2000L);
        }

        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            String address = gatt.getDevice().getAddress();
            BleBaseControl.this.mHandler.removeCallbacks(BleBaseControl.this.mDiscoverServiceTimeoutRunnable);
            if (status != 0) {
                BleBaseControl.this.disconnectDevice(address);
                return;
            }
            List services = gatt.getServices();
            for (BluetoothGattService bluetoothGattService : services) {
                Log.i((String)BleBaseControl.TAG, (String)("servicesUUID:" + bluetoothGattService.getUuid().toString()));
            }
            BleBaseControl.this.connecting = false;
            if (BleBaseControl.this.listener != null) {
                BleBaseControl.this.listener.bleServiceDiscovered(status, address);
            }
        }

        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i((String)BleBaseControl.TAG, (String)("a->w\uff1a" + DataTransferUtils.getHexString(characteristic.getValue())));
            if (BleBaseControl.this.listener != null) {
                BleBaseControl.this.listener.bleCharacteristicWrite(gatt.getDevice().getAddress(), characteristic.getUuid().toString(), status, characteristic.getValue());
            }
        }

        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if (BleBaseControl.this.listener != null) {
                BleBaseControl.this.listener.bleCharacteristicRead(gatt.getDevice().getAddress(), characteristic.getUuid().toString(), status, characteristic.getValue());
            }
        }

        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            Log.i((String)BleBaseControl.TAG, (String)("w->a\uff1a" + DataTransferUtils.getHexString(characteristic.getValue())));
            if (BleBaseControl.this.listener != null) {
                BleBaseControl.this.listener.bleCharacteristicChanged(gatt.getDevice().getAddress(), characteristic.getUuid().toString(), characteristic.getValue());
            }
        }

        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (BleBaseControl.this.listener != null) {
                BleBaseControl.this.listener.onDescriptorWrite(gatt, descriptor, status);
            }
            if (status == 0) {
                BleBaseControl.this.checkIsNotifyConfigAndRegisterCallback(descriptor, gatt);
            }
        }

        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            if (BleBaseControl.this.listener != null) {
                BleBaseControl.this.listener.onDescriptorRead(gatt, descriptor, status);
            }
        }

        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            if (BleBaseControl.this.listener != null) {
                BleBaseControl.this.listener.onReadRemoteRssi(gatt, rssi, status);
            }
        }

        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.i((String)BleBaseControl.TAG, (String)(mtu + ""));
        }
    };
    private Runnable mDiscoverServiceTimeoutRunnable = new Runnable(){

        @Override
        public void run() {
            BleBaseControl.this.disconnectDevice(BleBaseControl.this.mDeviceAddress);
        }
    };
    private Runnable mTimeoutRunnable = new Runnable(){

        @Override
        public void run() {
            BleBaseControl.this.mIsConnected = false;
            BleBaseControl.this.connecting = false;
            Log.i((String)BleBaseControl.TAG, (String)"\u6ca1\u6709\u6536\u5230\u7cfb\u7edf\u56de\u8c03\uff0c\u76f4\u63a5\u65ad\u5f00");
            BleBaseControl.this.disconnectDevice(BleBaseControl.this.mDeviceAddress);
            if (BleBaseControl.this.listener != null) {
                BleBaseControl.this.listener.bleNoCallback();
            }
        }
    };
    private Runnable mReconnectRunnable = new Runnable(){

        @Override
        public void run() {
            if (BleBaseControl.this.count.get() < BleBaseControl.this.maxReconnect) {
                BleBaseControl.this.count.incrementAndGet();
                Log.i((String)BleBaseControl.TAG, (String)("\u6b63\u5728\u91cd\u8fde,\u91cd\u8fde\u6b21\u6570\uff1a" + BleBaseControl.this.count.get()));
                BleBaseControl.this.connect(BleBaseControl.this.mDeviceAddress);
            } else {
                BleBaseControl.this.connecting = false;
                Log.i((String)BleBaseControl.TAG, (String)("\u8d85\u51fa\u4e86\u91cd\u8fde\u6b21\u6570:" + BleBaseControl.this.count.get()));
            }
        }
    };

    public void setBluetoothTurnOff(boolean bluetoothTurnOff) {
        this.bluetoothTurnOff = bluetoothTurnOff;
        if (!bluetoothTurnOff) {
            this.mIsConnected = false;
            this.connecting = false;
        }
    }

    public boolean ismIsConnected() {
        return this.mIsConnected;
    }

    public static BleBaseControl getInstance() {
        return bleBaseControl;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static BleBaseControl getInstance(Context context) {
        if (bleBaseControl != null) return bleBaseControl;
        Class<BleBaseControl> clazz = BleBaseControl.class;
        synchronized (BleBaseControl.class) {
            if (bleBaseControl != null) return bleBaseControl;
            bleBaseControl = new BleBaseControl(context);
            // ** MonitorExit[var1_1] (shouldn't be in output)
            return bleBaseControl;
        }
    }

    public Context getmContext() {
        return this.mContext;
    }

    public void setmContext(Context mContext) {
        this.mContext = mContext;
    }

    private BleBaseControl(Context context) {
        this.mContext = context;
        this.initialize();
    }

    private void initialize() {
        BluetoothManager bluetoothManager = (BluetoothManager)this.mContext.getSystemService("bluetooth");
        if (bluetoothManager != null) {
            this.mBluetoothAdapter = bluetoothManager.getAdapter();
        } else {
            Log.e((String)TAG, (String)"Unable to initialize BluetoothManager...");
        }
    }

    private void reconnectFromStateChange(String address) {
        this.mHandler.removeCallbacks((Runnable)this.systemProxyTimeoutRunnable);
        this.mHandler.postDelayed((Runnable)this.systemProxyTimeoutRunnable, 3000L);
    }

    private void doConnectClone() {
        boolean background = AppUtil.isBackground(this.mContext);
        boolean background1 = AppUtil.isApplicationBroughtToBackground(this.mContext);
        if (background || background1) {
            Log.i((String)TAG, (String)"\u540e\u53f0\u91cd\u8fde\u8c03\u7528");
            this.doConnect();
        } else {
            Log.i((String)TAG, (String)"\u524d\u53f0\u91cd\u8fde\u8c03\u7528");
            BleScannerHelper.getInstance().scanTheDevice(this.mContext, this.mDeviceAddress, new OnTheScanResult(){

                @Override
                public void onResult(BluetoothDevice bluetoothDevice) {
                    if (BleBaseControl.this.count.get() >= BleBaseControl.this.maxReconnect) {
                        BleBaseControl.this.count.getAndSet(0);
                    }
                    if (bluetoothDevice != null) {
                        BleBaseControl.this.mHandler.postDelayed(BleBaseControl.this.mReconnectRunnable, 200L);
                    } else {
                        BleBaseControl.this.connecting = false;
                        BleBaseControl.this.count.incrementAndGet();
                        BleBaseControl.this.doConnect();
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    BleBaseControl.this.connecting = false;
                    BleBaseControl.this.count.incrementAndGet();
                    BleBaseControl.this.doConnect();
                }
            });
        }
    }

    public void reconnectOpeningUp() {
        if (this.count.get() >= this.maxReconnect) {
            this.count.getAndSet(0);
        }
        if (this.failCount.get() >= this.maxFail) {
            this.failCount.getAndSet(0);
        }
        this.isNeedReconnect = true;
        this.reconnectDevice();
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void reconnectDevice() {
        Class<BleBaseControl> clazz = BleBaseControl.class;
        synchronized (BleBaseControl.class) {
            if (!this.isNeedReconnect) {
                this.connecting = false;
                // ** MonitorExit[var1_1] (shouldn't be in output)
                return;
            }
            if (!BluetoothUtils.isEnabledBluetooth(this.mContext) || TextUtils.isEmpty((CharSequence)this.mDeviceAddress)) {
                this.mIsConnected = false;
                this.connecting = false;
                // ** MonitorExit[var1_1] (shouldn't be in output)
                return;
            }
            if (this.failCount.get() >= this.maxFail) {
                this.mIsConnected = false;
                this.connecting = false;
                Log.i((String)TAG, (String)("\u5185\u90e8\u5931\u8d25\u5faa\u73af\u5927\u4e8e" + this.maxFail + "\u6b21\u76f4\u63a5\u8fd4\u56de"));
                // ** MonitorExit[var1_1] (shouldn't be in output)
                return;
            }
            if (this.isConnecting() || this.ismIsConnected()) {
                Log.i((String)TAG, (String)("\u6b63\u5728\u8fde\u63a5:" + this.isConnecting() + " \u5df2\u7ecf\u8fde\u4e0a:" + this.ismIsConnected()));
                // ** MonitorExit[var1_1] (shouldn't be in output)
                return;
            }
            this.mHandler.removeCallbacks(this.mReconnectRunnable);
            if (BleScannerCompat.getScanner(this.mContext).isScanning()) {
                // ** MonitorExit[var1_1] (shouldn't be in output)
                return;
            }
            this.doConnectClone();
            // ** MonitorExit[var1_1] (shouldn't be in output)
            return;
        }
    }

    private void doConnect() {
        if (!this.isNeedReconnect) {
            this.connecting = false;
            return;
        }
        if (this.count.get() % 3 == 0 && !this.bluetoothTurnOff) {
            this.mHandler.postDelayed(this.mReconnectRunnable, 1000L);
            Log.i((String)TAG, (String)"--\u76f4\u8fde");
        } else {
            Log.i((String)TAG, (String)"--\u626b\u8fde");
            this.bluetoothTurnOff = false;
            BleScannerHelper.getInstance().scanTheDevice(this.mContext, this.mDeviceAddress, new OnTheScanResult(){

                @Override
                public void onResult(BluetoothDevice bluetoothDevice) {
                    if (BleBaseControl.this.count.get() >= BleBaseControl.this.maxReconnect) {
                        BleBaseControl.this.count.getAndSet(0);
                    }
                    if (bluetoothDevice != null) {
                        BleBaseControl.this.mHandler.postDelayed(BleBaseControl.this.mReconnectRunnable, 200L);
                    } else {
                        BleBaseControl.this.connecting = false;
                        BleBaseControl.this.count.incrementAndGet();
                        BleBaseControl.this.doConnect();
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    BleBaseControl.this.connecting = false;
                    BleBaseControl.this.count.incrementAndGet();
                    BleBaseControl.this.doConnect();
                }
            });
        }
    }

    public boolean connect(String address) {
        BluetoothGatt gatt;
        if (!BluetoothUtils.isEnabledBluetooth(this.mContext)) {
            this.connecting = false;
            this.isNeedReconnect = false;
            return false;
        }
        if (TextUtils.isEmpty((CharSequence)address)) {
            Log.i((String)TAG, (String)"address \u7a7a\u8fd4\u56de");
            this.mIsConnected = false;
            this.connecting = false;
            return false;
        }
        if (this.isConnecting() || this.ismIsConnected()) {
            Log.i((String)TAG, (String)"\u518d\u6b21\u68c0\u67e5\u7684\u65f6\u5019\u8fd4\u56de\u4e86");
            return false;
        }
        this.mHandler.removeCallbacks(this.mTimeoutRunnable);
        this.mHandler.postDelayed(this.mTimeoutRunnable, 40000L);
        this.connecting = true;
        this.isNeedReconnect = true;
        BleScannerHelper.getInstance().stopScan(this.mContext);
        this.mDeviceAddress = address;
        BluetoothDevice device = this.mBluetoothAdapter.getRemoteDevice(address);
        Log.i((String)TAG, (String)"---------------\u3010\u5f00\u59cbGATT\u8fde\u63a5\u3011--------------");
        if (this.listener != null) {
            this.listener.startConnect();
        }
        if (Build.VERSION.SDK_INT > 23) {
            if (Build.VERSION.SDK_INT >= 31 && ActivityCompat.checkSelfPermission((Context)this.mContext, (String)"android.permission.BLUETOOTH_CONNECT") != 0) {
                return false;
            }
            gatt = device.connectGatt(this.mContext, false, this.mGattCallback, 2);
        } else {
            gatt = device.connectGatt(this.mContext, false, this.mGattCallback);
        }
        if (gatt == null) {
            this.mBluetoothGatt.remove(address);
            return false;
        }
        this.mBluetoothGatt.put(address, gatt);
        return true;
    }

    public void disconnectDevice(String address) {
        try {
            this.connecting = false;
            this.mIsConnected = false;
            Log.i((String)TAG, (String)(address + "  gatt map size:" + this.mBluetoothGatt.size()));
            for (final BluetoothGatt gatt : this.mBluetoothGatt.values()) {
                if (gatt == null) continue;
                Log.i((String)TAG, (String)"gatt disconnect it");
                if (Build.VERSION.SDK_INT >= 31 && ActivityCompat.checkSelfPermission((Context)this.mContext, (String)"android.permission.BLUETOOTH_CONNECT") != 0) {
                    return;
                }
                gatt.disconnect();
                this.mHandler.postDelayed(new Runnable(){

                    @Override
                    public void run() {
                        BleBaseControl.this.refreshDeviceCache(gatt);
                        if (Build.VERSION.SDK_INT >= 31 && ActivityCompat.checkSelfPermission((Context)BleBaseControl.this.mContext, (String)"android.permission.BLUETOOTH_CONNECT") != 0) {
                            return;
                        }
                        gatt.close();
                    }
                }, 500L);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public BluetoothGatt getGatt(String address) {
        if (this.mBluetoothGatt.containsKey(address)) {
            BluetoothGatt gatt = this.mBluetoothGatt.get(address);
            if (gatt == null) {
                return null;
            }
            return gatt;
        }
        return null;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void notifyMyAll() {
        Object object = this.mLock;
        synchronized (object) {
            this.mLock.notifyAll();
        }
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private void waitFor(long time) {
        Object object = this.mLock;
        synchronized (object) {
            try {
                this.mLock.wait(time);
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    protected boolean refreshDeviceCache(BluetoothGatt gatt) {
        if (!BluetoothUtils.isEnabledBluetooth(this.mContext)) {
            return false;
        }
        try {
            Method refresh = BluetoothGatt.class.getMethod("refresh", new Class[0]);
            if (refresh != null) {
                boolean success = (Boolean)refresh.invoke(gatt, new Object[0]);
                Log.i((String)TAG, (String)("Refreshing result: " + success));
                return success;
            }
        }
        catch (Exception e) {
            Log.i((String)TAG, (String)("An exception occured while refreshing device " + e.toString()));
        }
        return false;
    }

    public void setNeedReconnect(boolean needReconnect) {
        this.isNeedReconnect = needReconnect;
    }

    public void setmDeviceAddress(String mDeviceAddress) {
        this.mDeviceAddress = mDeviceAddress;
    }

    public String getmDeviceAddress() {
        return this.mDeviceAddress;
    }

    public IBleListener getListener() {
        return this.listener;
    }

    public void setListener(IBleListener listener) {
        this.listener = listener;
    }

    public boolean isConnecting() {
        return this.connecting;
    }

    public void setRtkBindTag(Boolean rtkBindTag) {
        this.rtkBindTag = rtkBindTag;
    }

    public BluetoothGattCharacteristic findTheGattCharacteristic(UUID serviceUuid, UUID charUuid) {
        BluetoothGattCharacteristic charCharacteristic = this.cacheGattCharacteristicHashMap.get(charUuid);
        if (charCharacteristic == null) {
            charCharacteristic = this.initTheCharacteristic(serviceUuid, charUuid);
            if (charCharacteristic != null) {
                this.cacheGattCharacteristicHashMap.put(charUuid, charCharacteristic);
            }
            return charCharacteristic;
        }
        return charCharacteristic;
    }

    private BluetoothGattCharacteristic initTheCharacteristic(UUID serviceUuid, UUID charUuid) {
        if (!BleOperateManager.getInstance().isConnected()) {
            return null;
        }
        String address = BleBaseControl.getInstance().getmDeviceAddress();
        if (TextUtils.isEmpty((CharSequence)address)) {
            return null;
        }
        BluetoothGatt gatt = BleBaseControl.getInstance().getGatt(address);
        if (gatt == null) {
            return null;
        }
        BluetoothGattService service = gatt.getService(serviceUuid);
        if (service == null) {
            Log.e((String)TAG, (String)("initTheCharacteristic: can't find service uuid=" + serviceUuid));
            return null;
        }
        BluetoothGattCharacteristic charCharacteristic = service.getCharacteristic(charUuid);
        return charCharacteristic;
    }

    private void checkIsNotifyConfigAndRegisterCallback(BluetoothGattDescriptor descriptor, BluetoothGatt gatt) {
        byte[] value;
        if (descriptor.getUuid().compareTo(Constants.GATT_NOTIFY_CONFIG) == 0 && (value = descriptor.getValue()) != null && value.length == 2 && value[1] == 0 && value[0] == 1 && this.listener != null) {
            this.listener.bleCharacteristicNotification();
        }
    }

    public void unBondedDevice(String mac) {
        try {
            if (TextUtils.isEmpty((CharSequence)mac)) {
                return;
            }
            BluetoothDevice device = this.mBluetoothAdapter.getRemoteDevice(mac);
            Class<BluetoothDevice> btDeviceCls = BluetoothDevice.class;
            Method removeBond = null;
            try {
                removeBond = btDeviceCls.getMethod("removeBond", new Class[0]);
                removeBond.invoke(device, new Object[0]);
            }
            catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            catch (IllegalAccessException e) {
                e.printStackTrace();
            }
            catch (InvocationTargetException e) {
                e.printStackTrace();
            }
            removeBond.setAccessible(true);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean classicBluetoothScan() {
        try {
            if (this.mBluetoothAdapter.isDiscovering()) {
                this.mBluetoothAdapter.cancelDiscovery();
            }
            Log.i((String)TAG, (String)"------\u626b\u63cf");
            return this.mBluetoothAdapter.startDiscovery();
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @SuppressLint(value={"MissingPermission"})
    public boolean cancelScanBluetooth() {
        try {
            return this.mBluetoothAdapter.cancelDiscovery();
        }
        catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    @SuppressLint(value={"MissingPermission"})
    public void createBondBlueTooth(BluetoothDevice device) {
        if (device == null) {
            return;
        }
        try {
            if (this.mBluetoothAdapter.isDiscovering()) {
                this.mBluetoothAdapter.cancelDiscovery();
            }
            if (device.getBondState() == 10) {
                try {
                    boolean returnValue = device.createBond();
                    Log.i((String)TAG, (String)("\u662f\u5426\u914d\u5bf9\u6210\u529f\uff1a" + returnValue));
                }
                catch (Exception e) {
                    e.printStackTrace();
                    Log.i((String)TAG, (String)"\u914d\u5bf9\u5931\u8d25\uff1a");
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SuppressLint(value={"DiscouragedPrivateApi"})
    public boolean createBond(BluetoothDevice device, int transport) {
        if (null != device) {
            boolean bRet = false;
            try {
                Class<?> bluetoothDeviceClass = device.getClass();
                Method createBondMethod = bluetoothDeviceClass.getDeclaredMethod("createBond", Integer.TYPE);
                createBondMethod.setAccessible(true);
                Object object = createBondMethod.invoke(device, transport);
                if (!(object instanceof Boolean)) {
                    return false;
                }
                bRet = (Boolean)object;
            }
            catch (Exception var7) {
                var7.printStackTrace();
            }
            return bRet;
        }
        return false;
    }

    @SuppressLint(value={"MissingPermission"})
    public boolean createBond(Context context, BluetoothDevice device) {
        if (device != null) {
            boolean bRet = false;
            if (Build.VERSION.SDK_INT >= 20) {
                bRet = device.createBond();
            } else {
                Class<?> btClass = device.getClass();
                try {
                    Method createBondMethod = btClass.getMethod("createBond", new Class[0]);
                    Object object = createBondMethod.invoke(device, new Object[0]);
                    if (!(object instanceof Boolean)) {
                        return false;
                    }
                    bRet = (Boolean)object;
                }
                catch (Exception var6) {
                    var6.printStackTrace();
                    Log.i((String)TAG, (String)("Invoke createBond : " + var6.getMessage()));
                }
            }
            return bRet;
        }
        return false;
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
        try {
            if (this.bleConnectDevice != null) {
                BluetoothDevice device = this.getMacSystemBond(this.bleConnectDevice.getAddress());
                Log.i((String)TAG, (String)("-----\u914d\u5bf9\u72b6\u6001:" + this.bleConnectDevice.getBondState()));
                if (this.bleConnectDevice.getBondState() == 10 && device == null) {
                    this.bleConnectDevice.createBond();
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class SystemProxyTimeoutRunnable
    implements Runnable {
        private SystemProxyTimeoutRunnable() {
        }

        @Override
        public void run() {
            BleBaseControl.this.doConnectClone();
        }
    }
}

