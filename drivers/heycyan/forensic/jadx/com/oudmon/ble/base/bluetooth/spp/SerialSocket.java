/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.annotation.SuppressLint
 *  android.bluetooth.BluetoothDevice
 *  android.bluetooth.BluetoothSocket
 */
package com.oudmon.ble.base.bluetooth.spp;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import com.oudmon.ble.base.bluetooth.spp.SerialListener;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.Executors;

public class SerialSocket
implements Runnable {
    private static final UUID BLUETOOTH_SPP_JieLi = UUID.fromString("FE010000-1234-5678-ABCD-00805F9B34FB");
    private SerialListener listener;
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private static SerialSocket serialSocket;

    private SerialSocket() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static SerialSocket getInstance() {
        if (serialSocket != null) return serialSocket;
        Class<SerialSocket> clazz = SerialSocket.class;
        synchronized (SerialSocket.class) {
            if (serialSocket != null) return serialSocket;
            serialSocket = new SerialSocket();
            // ** MonitorExit[var0] (shouldn't be in output)
            return serialSocket;
        }
    }

    public void setListener(SerialListener listener) {
        this.listener = listener;
    }

    public void setDevice(BluetoothDevice device) {
        this.device = device;
    }

    public void connect(SerialListener listener) {
        this.listener = listener;
        Executors.newSingleThreadExecutor().submit(this);
    }

    public void connect() {
        Executors.newSingleThreadExecutor().submit(this);
    }

    public boolean isConnected() {
        if (this.socket == null) {
            return false;
        }
        return this.socket.isConnected();
    }

    public void disconnect() {
        block4: {
            try {
                this.listener = null;
                if (this.socket == null) break block4;
                try {
                    this.socket.close();
                }
                catch (Exception exception) {
                    // empty catch block
                }
                this.socket = null;
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void write(byte[] data) {
        try {
            if (this.socket == null) {
                if (this.listener != null) {
                    this.listener.onSerialConnect();
                }
                return;
            }
            this.socket.getOutputStream().write(data);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void write(byte[] data, int off, int len) {
        try {
            this.socket.getOutputStream().write(data, off, len);
        }
        catch (IOException e) {
            if (this.listener != null) {
                this.listener.onSerialIoError(e);
            }
            e.printStackTrace();
        }
    }

    @Override
    @SuppressLint(value={"MissingPermission"})
    public void run() {
        try {
            this.socket = this.device.createRfcommSocketToServiceRecord(BLUETOOTH_SPP_JieLi);
            this.socket.connect();
            if (this.listener != null) {
                this.listener.onSerialConnect();
            }
        }
        catch (Exception e) {
            if (this.listener != null) {
                this.listener.onSerialConnectError(e);
            }
            try {
                this.socket.close();
            }
            catch (Exception exception) {
                // empty catch block
            }
            this.socket = null;
            return;
        }
        try {
            byte[] buffer = new byte[1024];
            while (true) {
                int len = this.socket.getInputStream().read(buffer);
                byte[] data = Arrays.copyOf(buffer, len);
                if (this.listener == null || data.length <= 0) continue;
                this.listener.onSerialRead(data);
            }
        }
        catch (Exception e) {
            if (this.listener != null) {
                this.listener.onSerialIoError(e);
            }
            try {
                this.socket.close();
            }
            catch (Exception exception) {
                // empty catch block
            }
            this.socket = null;
            return;
        }
    }
}

