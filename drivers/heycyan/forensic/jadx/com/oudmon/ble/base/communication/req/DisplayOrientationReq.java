/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class DisplayOrientationReq
extends MixtureReq {
    private DisplayOrientationReq() {
        super((byte)41);
        this.subData = new byte[]{1};
    }

    private DisplayOrientationReq(boolean isPortrait, boolean isLeft) {
        super((byte)41);
        this.subData = new byte[]{2, (byte)(isPortrait ? 1 : 2), (byte)(isPortrait ? 0 : (isLeft ? 1 : 2))};
    }

    public static DisplayOrientationReq getReadInstance() {
        return new DisplayOrientationReq();
    }

    public static DisplayOrientationReq getWriteInstance(boolean isPortrait, boolean isLeft) {
        return new DisplayOrientationReq(isPortrait, isLeft);
    }
}

