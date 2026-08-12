/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.IntentFilter
 */
package com.oudmon.ble.base.bluetooth;

import android.content.IntentFilter;

public class BleAction {
    public static final String BLE_NOT_SUPPORTED = "com.swatchdevice.pro.sdk.ble.not_supported";
    public static final String BLE_NO_BT_ADAPTER = "com.swatchdevice.pro.sdk.ble.no_bt_adapter";
    public static final String BLE_STATUS_ABNORMAL = "com.swatchdevice.pro.sdk.ble.status_abnormal";
    public static final String BLE_START_CONNECT = "com.swatchdevice.pro.sdk.ble.start_connect";
    public static final String BLE_GATT_CONNECTED = "com.swatchdevice.pro.sdk.ble.gatt_connected";
    public static final String BLE_GATT_DISCONNECTED = "com.swatchdevice.pro.sdk.ble.gatt_disconnected";
    public static final String BLE_SERVICE_DISCOVERED = "com.swatchdevice.pro.sdk.ble.service_discovered";
    public static final String BLE_CHARACTERISTIC_READ = "com.swatchdevice.pro.sdk.ble.characteristic_read";
    public static final String BLE_CHARACTERISTIC_DISCOVERED = "com.swatchdevice.pro.sdk.ble.characteristic_notification_qc";
    public static final String BLE_CHARACTERISTIC_WRITE = "com.swatchdevice.pro.characteristic_write_qc";
    public static final String BLE_CHARACTERISTIC_CHANGED = "com.swatchdevice.pro.characteristic_changed_qc";
    public static final String BLE_NO_CALLBACK = "com.swatchdevice.pro.sdk.ble.BLE_NO_CALLBACK";
    public static final String BLE_STATUS = "com.swatchdevice.pro.sdk.ble.BLE_STATUS";
    public static final String EXTRA_DEVICE = "DEVICE";
    public static final String EXTRA_ADDR = "ADDRESS";
    public static final String EXTRA_CONNECTED = "CONNECTED";
    public static final String EXTRA_STATUS = "STATUS";
    public static final String EXTRA_CHARACTER_UUID = "CHARACTER_UUID";
    public static final String EXTRA_VALUE = "VALUE";
    public static final String EXTRA_DATA = "DATA";
    public static final String EXTRA_BLE_STATUS = "EXTRA_STATUS";
    public static final String EXTRA_BLE_NEW_STATE = "EXTRA_BLE_NEW_STATE";

    public static IntentFilter getIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BLE_NOT_SUPPORTED);
        intentFilter.addAction(BLE_NO_BT_ADAPTER);
        intentFilter.addAction(BLE_STATUS_ABNORMAL);
        intentFilter.addAction(BLE_GATT_CONNECTED);
        intentFilter.addAction(BLE_GATT_DISCONNECTED);
        intentFilter.addAction(BLE_SERVICE_DISCOVERED);
        intentFilter.addAction(BLE_CHARACTERISTIC_READ);
        intentFilter.addAction(BLE_CHARACTERISTIC_DISCOVERED);
        intentFilter.addAction(BLE_CHARACTERISTIC_WRITE);
        intentFilter.addAction(BLE_CHARACTERISTIC_CHANGED);
        intentFilter.addAction(BLE_START_CONNECT);
        intentFilter.addAction(BLE_NO_CALLBACK);
        intentFilter.addAction(BLE_STATUS);
        return intentFilter;
    }

    public static IntentFilter getDeviceIntentFilter() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED");
        intentFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED");
        intentFilter.addAction("android.bluetooth.device.action.ACL_CONNECTED");
        intentFilter.addAction("android.bluetooth.device.action.ACL_DISCONNECTED");
        intentFilter.addAction("android.bluetooth.device.action.FOUND");
        intentFilter.addAction("android.intent.action.SCREEN_OFF");
        intentFilter.addAction("android.intent.action.SCREEN_ON");
        intentFilter.addAction("android.intent.action.USER_PRESENT");
        intentFilter.addAction("android.intent.action.TIME_SET");
        intentFilter.addAction("android.intent.action.DATE_CHANGED");
        intentFilter.addAction("android.intent.action.TIME_TICK");
        intentFilter.addAction("android.intent.action.INPUT_METHOD_CHANGED");
        intentFilter.addAction("android.intent.action.CLOSE_SYSTEM_DIALOGS");
        intentFilter.addAction("android.intent.action.BATTERY_OKAY");
        intentFilter.addAction("android.intent.action.BATTERY_CHANGED");
        intentFilter.addAction("android.intent.action.CAMERA_BUTTON");
        intentFilter.addAction("android.intent.action.DATE_CHANGED");
        intentFilter.addAction("android.net.conn.CONNECTIVITY_CHANGE");
        return intentFilter;
    }
}

