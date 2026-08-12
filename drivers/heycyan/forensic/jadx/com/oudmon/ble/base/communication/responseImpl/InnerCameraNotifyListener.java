/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.content.Context
 */
package com.oudmon.ble.base.communication.responseImpl;

import android.content.Context;
import com.oudmon.ble.base.communication.ICommandResponse;
import com.oudmon.ble.base.communication.rsp.CameraNotifyRsp;

public class InnerCameraNotifyListener
implements ICommandResponse<CameraNotifyRsp> {
    private Context mContext;
    private ICommandResponse<CameraNotifyRsp> outRspIOdmOpResponse;

    public InnerCameraNotifyListener(Context context) {
        this.mContext = context;
    }

    public ICommandResponse<CameraNotifyRsp> getOutRspIOdmOpResponse() {
        return this.outRspIOdmOpResponse;
    }

    public void setOutRspIOdmOpResponse(ICommandResponse<CameraNotifyRsp> outRspIOdmOpResponse) {
        this.outRspIOdmOpResponse = outRspIOdmOpResponse;
    }

    @Override
    public void onDataResponse(CameraNotifyRsp resultEntity) {
        if (this.outRspIOdmOpResponse != null) {
            this.outRspIOdmOpResponse.onDataResponse(resultEntity);
        } else if (resultEntity.getStatus() != 0 || resultEntity.getAction() == 1) {
            // empty if block
        }
    }
}

