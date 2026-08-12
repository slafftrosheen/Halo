/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class GlassModelControlReq
extends MixtureReq {
    public GlassModelControlReq(int dataType, int glassWorkType) {
        super((byte)62);
        this.subData = new byte[]{2, (byte)dataType, (byte)glassWorkType};
    }
}

