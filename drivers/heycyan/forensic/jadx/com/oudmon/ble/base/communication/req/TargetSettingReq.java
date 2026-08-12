/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;
import com.oudmon.qc_utils.bytes.DataTransferUtils;

public class TargetSettingReq
extends MixtureReq {
    private TargetSettingReq() {
        super((byte)33);
    }

    public static TargetSettingReq getReadInstance() {
        return new TargetSettingReq(){
            {
                this.subData = new byte[]{1};
            }
        };
    }

    public static TargetSettingReq getWriteInstance(final int step, final int calorie, final int distance) {
        return new TargetSettingReq(){
            {
                this.subData = new byte[10];
                this.subData[0] = 2;
                System.arraycopy(DataTransferUtils.intToBytes(step), 0, this.subData, 1, 3);
                System.arraycopy(DataTransferUtils.intToBytes(calorie), 0, this.subData, 4, 3);
                System.arraycopy(DataTransferUtils.intToBytes(distance), 0, this.subData, 7, 3);
            }
        };
    }

    public static TargetSettingReq getWriteInstance(final int step, final int calorie, final int distance, final int sportMinute, final int sleepMinute) {
        return new TargetSettingReq(){
            {
                this.subData = new byte[14];
                this.subData[0] = 2;
                System.arraycopy(DataTransferUtils.intToBytes(step), 0, this.subData, 1, 3);
                System.arraycopy(DataTransferUtils.intToBytes(calorie), 0, this.subData, 4, 3);
                System.arraycopy(DataTransferUtils.intToBytes(distance), 0, this.subData, 7, 3);
                System.arraycopy(DataTransferUtils.shortToBytes((short)sportMinute), 0, this.subData, 10, 2);
                System.arraycopy(DataTransferUtils.shortToBytes((short)sleepMinute), 0, this.subData, 12, 2);
            }
        };
    }
}

