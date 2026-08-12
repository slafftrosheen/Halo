/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class AgpsReq
extends MixtureReq {
    private AgpsReq() {
        super((byte)48);
    }

    public static AgpsReq getReadInstance() {
        return new AgpsReq(){
            {
                this.subData = new byte[]{1};
            }
        };
    }

    public static AgpsReq getWriteInstance(final boolean enable) {
        return new AgpsReq(){
            {
                this.subData = new byte[]{2, (byte)(enable ? 1 : 0)};
            }
        };
    }
}

