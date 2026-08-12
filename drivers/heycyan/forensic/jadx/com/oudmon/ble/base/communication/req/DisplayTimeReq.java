/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;

public class DisplayTimeReq
extends MixtureReq {
    private DisplayTimeReq() {
        super((byte)31);
    }

    public static DisplayTimeReq getReadInstance() {
        return new DisplayTimeReq(){
            {
                this.subData = new byte[]{1};
            }
        };
    }

    public static DisplayTimeReq getWriteInstance(final int displayTime, final int displayType, final int alpha, final int total, final int curr) {
        return new DisplayTimeReq(){
            {
                this.subData = new byte[]{2, (byte)displayTime, (byte)displayType, (byte)alpha, 0, (byte)total, (byte)curr};
            }
        };
    }

    public static DisplayTimeReq getWriteInstanceNew(final int displayTime, final int displayType, final int alpha, final int total, final int curr, final boolean open) {
        return new DisplayTimeReq(){
            {
                this.subData = new byte[]{2, (byte)displayTime, (byte)displayType, (byte)alpha, 0, (byte)total, (byte)curr, 5, 30, 5, (byte)(open ? 2 : 1)};
            }
        };
    }

    public static DisplayTimeReq getDeleteInstance() {
        return new DisplayTimeReq(){
            {
                this.subData = new byte[]{3};
            }
        };
    }
}

