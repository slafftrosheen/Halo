/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class PhoneSportReq
extends MixtureReq {
    private PhoneSportReq() {
        super((byte)119);
    }

    public static PhoneSportReq getSportStatus(final byte status, final byte sportType) {
        return new PhoneSportReq(){
            {
                this.subData = new byte[]{status, sportType};
            }
        };
    }
}

