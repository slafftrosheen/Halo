/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.entity.StartEndTimeEntity;
import com.oudmon.ble.base.communication.req.MixtureReq;

public class DndReq
extends MixtureReq {
    private DndReq() {
        super((byte)6);
        this.subData = new byte[]{1};
    }

    private DndReq(boolean isEnable, StartEndTimeEntity dndEntity) {
        super((byte)6);
        this.subData = new byte[]{2, (byte)(isEnable ? 1 : 2), (byte)(dndEntity.getStartHour() & 0xFF), (byte)(dndEntity.getStartMinute() & 0xFF), (byte)(dndEntity.getEndHour() & 0xFF), (byte)(dndEntity.getEndMinute() & 0xFF)};
    }

    public static DndReq getReadInstance() {
        return new DndReq();
    }

    public static DndReq getWriteInstance(boolean isEnable, StartEndTimeEntity dndEntity) {
        return new DndReq(isEnable, dndEntity);
    }
}

