/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.bluetooth.BluetoothDevice
 *  android.bluetooth.BluetoothGatt
 *  android.bluetooth.BluetoothGattDescriptor
 */
package com.oudmon.ble.base.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattDescriptor;
import com.oudmon.ble.base.request.BaseRequest;

public interface IBleListener {
    public void startConnect();

    public void bleGattConnected(BluetoothDevice var1);

    public void bleGattDisconnect(BluetoothDevice var1);

    public void bleServiceDiscovered(int var1, String var2);

    public void bleStatus(int var1, int var2);

    public void bleCharacteristicRead(String var1, String var2, int var3, byte[] var4);

    public void bleCharacteristicNotification();

    public void bleCharacteristicWrite(String var1, String var2, int var3, byte[] var4);

    public void bleCharacteristicChanged(String var1, String var2, byte[] var3);

    public void bleNoCallback();

    public boolean execute(BaseRequest var1);

    public void onReadRemoteRssi(BluetoothGatt var1, int var2, int var3);

    public void onDescriptorRead(BluetoothGatt var1, BluetoothGattDescriptor var2, int var3);

    public void onDescriptorWrite(BluetoothGatt var1, BluetoothGattDescriptor var2, int var3);

    public boolean isConnected();
}

