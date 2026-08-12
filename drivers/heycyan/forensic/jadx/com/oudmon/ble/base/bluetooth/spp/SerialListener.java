/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.bluetooth.spp;

import java.util.ArrayDeque;

public interface SerialListener {
    public void onSerialConnect();

    public void onSerialConnectError(Exception var1);

    public void onSerialRead(byte[] var1);

    public void onSerialRead(ArrayDeque<byte[]> var1);

    public void onSerialIoError(Exception var1);
}

