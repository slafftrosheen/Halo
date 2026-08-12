/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.rsp;

import android.util.Log;
import com.oudmon.ble.base.communication.entity.BpDataEntity;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import java.util.Arrays;

public class BpDataRsp
extends BaseRspCmd {
    private int valueIndex = 0;
    private BpDataEntity bpDataEntity;

    @Override
    public boolean acceptData(byte[] data) {
        Log.i((String)"Jxr35", (String)("acceptData.. data: " + DataTransferUtils.getHexString(data)));
        byte pocketIndex = data[0];
        if (pocketIndex == 0) {
            this.valueIndex = 0;
            this.bpDataEntity = new BpDataEntity(data[1] + 2000, data[2], data[3], data[4]);
            byte timeDelay = data[4];
            for (int i = 0; i < 6; ++i) {
                byte cur = data[i + 5];
                for (int j = 0; j < 8; ++j) {
                    byte mask = (byte)(cur >>> j);
                    if ((1 & mask) != 1) continue;
                    this.bpDataEntity.addBpIndex((i * 8 + j) * timeDelay);
                }
            }
        } else if (pocketIndex == 1) {
            if (this.bpDataEntity == null) {
                return true;
            }
            this.bpDataEntity.addRealValue(this.valueIndex * 13, Arrays.copyOfRange(data, 1, data.length));
            ++this.valueIndex;
            Log.i((String)"Jxr35", (String)("acceptData: size=" + this.bpDataEntity.getBpValues().size() + " cur offset=" + this.valueIndex * 13));
            if (this.valueIndex * 13 >= this.bpDataEntity.getBpValues().size()) {
                Log.i((String)"Jxr35", (String)"acceptData: \u6210\u529f");
                return false;
            }
        } else if ((pocketIndex & 0xFF) == 255) {
            return false;
        }
        return true;
    }

    public BpDataEntity getBpDataEntity() {
        return this.bpDataEntity;
    }
}

