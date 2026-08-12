/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.resp;

import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.util.Arrays;

public class GlassesTouchSupportRsp
extends BaseResponse {
    int glassesModel;
    boolean translationSupport;
    boolean wearCheckSupport;
    boolean volumeControl;

    @Override
    public boolean acceptData(byte[] data) {
        try {
            if (data.length <= 9) {
                this.glassesModel = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 7, 8));
                this.translationSupport = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 8, 9)) == 1;
                this.wearCheckSupport = true;
            } else if (data.length == 10) {
                this.glassesModel = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 7, 8));
                this.translationSupport = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 8, 9)) == 1;
                this.wearCheckSupport = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 9, 10)) == 0;
            } else {
                this.glassesModel = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 7, 8));
                this.translationSupport = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 8, 9)) == 1;
                this.wearCheckSupport = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 9, 10)) == 0;
                this.volumeControl = (data[10] & 1) != 0;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getGlassesModel() {
        return this.glassesModel;
    }

    public boolean isTranslationSupport() {
        return this.translationSupport;
    }

    public boolean isWearCheckSupport() {
        return this.wearCheckSupport;
    }

    public boolean isVolumeControl() {
        return this.volumeControl;
    }
}

