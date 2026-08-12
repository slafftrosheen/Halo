/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;
import java.nio.charset.StandardCharsets;

public class MusicSwitchReq
extends BaseReqCmd {
    protected byte[] data;

    private MusicSwitchReq() {
        super((byte)28);
    }

    public static MusicSwitchReq getReadInstance() {
        return new MusicSwitchReq(){
            {
                this.data = new byte[]{1};
            }
        };
    }

    public static MusicSwitchReq getWriteInstance(final boolean enable) {
        return new MusicSwitchReq(){
            {
                this.data = new byte[]{2, (byte)(enable ? 1 : 2)};
            }
        };
    }

    public static MusicSwitchReq getNewWriteInstance(final boolean playing, final int progress, final int volume, final String name) {
        return new MusicSwitchReq(){
            {
                byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
                byte[] data = new byte[nameBytes.length + 3];
                data[0] = (byte)(!playing ? 1 : 0);
                data[1] = (byte)progress;
                data[2] = (byte)volume;
                System.arraycopy(nameBytes, 0, data, 3, nameBytes.length);
                this.data = data;
            }
        };
    }

    @Override
    protected byte[] getSubData() {
        return this.data;
    }
}

