/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class DeviceSupportReq
extends MixtureReq {
    private DeviceSupportReq() {
        super((byte)60);
    }

    public static DeviceSupportReq getReadInstance() {
        return new DeviceSupportReq();
    }
}

