/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class SimpleKeyPowerOffReq
extends MixtureReq {
    public SimpleKeyPowerOffReq() {
        super((byte)8);
        this.subData = new byte[]{1};
    }
}

