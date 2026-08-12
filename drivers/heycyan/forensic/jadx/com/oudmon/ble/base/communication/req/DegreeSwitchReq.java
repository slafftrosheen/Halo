/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class DegreeSwitchReq
extends MixtureReq {
    private DegreeSwitchReq() {
        super((byte)25);
    }

    public static DegreeSwitchReq getReadInstance() {
        return new DegreeSwitchReq(){
            {
                this.subData = new byte[]{1};
            }
        };
    }

    public static DegreeSwitchReq getWriteInstance(final boolean enable, final boolean isCelsius) {
        return new DegreeSwitchReq(){
            {
                this.subData = new byte[]{2, (byte)(enable ? 1 : 2), (byte)(isCelsius ? 1 : 2)};
            }
        };
    }
}

