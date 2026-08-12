/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.bigData.resp;

import android.util.Log;
import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.util.Arrays;

public class GlassModelControlResponse
extends BaseResponse {
    private int dataType;
    private int glassWorkType;
    private int imageCount;
    private int videoCount;
    private int recordCount;
    private String p2pIp;
    private int errorCode = 1;
    private int workTypeIng = 0;
    private int videoAngle;
    private int videoDuration;
    private int otaStatus;
    private int recordAudioDuration;

    @Override
    public boolean acceptData(byte[] data) {
        try {
            this.dataType = data[7];
            if (this.dataType == 4) {
                this.imageCount = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 8, 10));
                this.videoCount = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 10, 12));
                this.recordCount = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 12, 14));
            } else if (this.dataType == 1) {
                this.glassWorkType = data[8];
                if (this.glassWorkType == 1 || this.glassWorkType == 2 || this.glassWorkType == 4 || this.glassWorkType == 6 || this.glassWorkType == 8 || this.glassWorkType == 7 || this.glassWorkType == 11 || this.glassWorkType == 12 || this.glassWorkType == 18) {
                    if (data.length > 9) {
                        this.errorCode = data[9];
                        if (this.errorCode != 0) {
                            Log.i((String)"GLASSES_LOG", (String)"\u6682\u65f6\u4e0d\u5904\u7406");
                        } else {
                            this.workTypeIng = data[10];
                            Log.i((String)"GLASSES_LOG", (String)("workTypeIng->" + this.workTypeIng));
                        }
                    }
                } else if (this.glassWorkType == 5 && this.errorCode != 0) {
                    this.otaStatus = this.errorCode;
                }
            } else if (this.dataType == 3) {
                this.p2pIp = ByteUtil.byteToInt(data[10]) + "." + ByteUtil.byteToInt(data[11]) + "." + ByteUtil.byteToInt(data[12]) + "." + ByteUtil.byteToInt(data[12]);
            } else if (this.dataType == 2) {
                this.videoAngle = data[8];
                this.videoDuration = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 9, 11));
            } else if (this.dataType == 6) {
                this.recordAudioDuration = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 9, 11));
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public int getDataType() {
        return this.dataType;
    }

    public int getGlassWorkType() {
        return this.glassWorkType;
    }

    public int getImageCount() {
        return this.imageCount;
    }

    public int getVideoCount() {
        return this.videoCount;
    }

    public int getRecordCount() {
        return this.recordCount;
    }

    public String getP2pIp() {
        return this.p2pIp;
    }

    public int getErrorCode() {
        return this.errorCode;
    }

    public int getWorkTypeIng() {
        return this.workTypeIng;
    }

    public int getVideoAngle() {
        return this.videoAngle;
    }

    public int getVideoDuration() {
        return this.videoDuration;
    }

    public int getOtaStatus() {
        return this.otaStatus;
    }

    public int getRecordAudioDuration() {
        return this.recordAudioDuration;
    }
}

