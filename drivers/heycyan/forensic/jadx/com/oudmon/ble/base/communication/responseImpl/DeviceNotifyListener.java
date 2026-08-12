/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.responseImpl;

import com.oudmon.ble.base.communication.ICommandResponse;
import com.oudmon.ble.base.communication.rsp.DeviceNotifyRsp;
import java.util.concurrent.ConcurrentHashMap;

public class DeviceNotifyListener
implements ICommandResponse<DeviceNotifyRsp> {
    private ConcurrentHashMap<Integer, ICommandResponse<DeviceNotifyRsp>> respList = new ConcurrentHashMap();

    public void setOutRspIOdmOpResponse(int key, ICommandResponse<DeviceNotifyRsp> outRspIOdmOpResponse) {
        this.respList.put(key, outRspIOdmOpResponse);
        if (this.respList.get(100) != null) {
            this.removeOtherCallbacks();
        }
    }

    public void removeCallback(int key) {
        this.respList.remove(key);
    }

    public void removeOtherCallbacks() {
        this.respList.remove(1);
        this.respList.remove(2);
        this.respList.remove(3);
        this.respList.remove(5);
        this.respList.remove(7);
        this.respList.remove(9);
    }

    @Override
    public void onDataResponse(DeviceNotifyRsp resultEntity) {
        for (ICommandResponse<DeviceNotifyRsp> outRspIOdmOpResponse : this.respList.values()) {
            outRspIOdmOpResponse.onDataResponse(resultEntity);
        }
    }
}

