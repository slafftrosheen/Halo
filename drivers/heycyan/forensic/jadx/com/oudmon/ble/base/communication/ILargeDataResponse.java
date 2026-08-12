/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication;

import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;

public interface ILargeDataResponse<T extends BaseResponse> {
    public void parseData(int var1, T var2);
}

