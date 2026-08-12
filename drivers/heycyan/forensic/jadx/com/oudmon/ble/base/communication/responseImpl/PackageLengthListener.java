/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.responseImpl;

import com.oudmon.ble.base.communication.ICommandResponse;
import com.oudmon.ble.base.communication.JPackageManager;
import com.oudmon.ble.base.communication.LargeDataHandler;
import com.oudmon.ble.base.communication.rsp.PackageLengthRsp;

public class PackageLengthListener
implements ICommandResponse<PackageLengthRsp> {
    @Override
    public void onDataResponse(PackageLengthRsp resultEntity) {
        JPackageManager.getInstance().setLength(Math.max(resultEntity.mData, 244));
        LargeDataHandler.getInstance().packageLength();
    }
}

