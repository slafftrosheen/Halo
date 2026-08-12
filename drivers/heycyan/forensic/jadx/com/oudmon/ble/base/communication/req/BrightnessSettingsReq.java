/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class BrightnessSettingsReq
extends MixtureReq {
    private BrightnessSettingsReq() {
        super((byte)27);
    }

    public static BrightnessSettingsReq getReadInstance() {
        return new BrightnessSettingsReq(){
            {
                this.subData = new byte[]{1};
            }
        };
    }

    public static BrightnessSettingsReq getWriteInstance(final int level) {
        return new BrightnessSettingsReq(){
            {
                this.subData = new byte[]{2, (byte)level};
            }
        };
    }
}

