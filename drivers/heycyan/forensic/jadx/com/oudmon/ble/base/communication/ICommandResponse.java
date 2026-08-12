/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;

public interface ICommandResponse<T extends BaseRspCmd> {
    public void onDataResponse(T var1);
}

