/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.os.Build
 *  android.os.Build$VERSION
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.req;

import android.os.Build;
import android.util.Log;
import com.oudmon.ble.base.communication.req.BaseReqCmd;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class BindAncsReq
extends BaseReqCmd {
    private byte[] mData;

    public BindAncsReq() {
        super((byte)4);
        try {
            Log.i((String)"Jxr35", (String)("\u624b\u673a\u5382\u5546: " + Build.BRAND + "\u624b\u673a\u578b\u53f7: " + Build.MODEL + "SDK\u7248\u672c: " + Build.VERSION.SDK_INT + "\u7cfb\u7edf\u7248\u672c: " + Build.VERSION.RELEASE));
            byte[] model = Build.MODEL.getBytes(StandardCharsets.UTF_8);
            if (model.length >= 14) {
                model = Arrays.copyOf(model, 13);
            }
            int androidVersion = 10;
            if (Build.VERSION.SDK_INT == 29) {
                androidVersion = 10;
            } else if (Build.VERSION.SDK_INT == 28) {
                androidVersion = 9;
            } else if (Build.VERSION.SDK_INT == 27 || Build.VERSION.SDK_INT == 26) {
                androidVersion = 8;
            } else if (Build.VERSION.SDK_INT == 25 || Build.VERSION.SDK_INT == 24) {
                androidVersion = 7;
            } else if (Build.VERSION.SDK_INT == 23) {
                androidVersion = 6;
            } else if (Build.VERSION.SDK_INT == 22 || Build.VERSION.SDK_INT == 21) {
                androidVersion = 5;
            }
            this.mData = new byte[model.length + 2];
            this.mData[0] = 2;
            this.mData[1] = androidVersion;
            System.arraycopy(model, 0, this.mData, 2, model.length);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    protected byte[] getSubData() {
        byte[] byArray;
        if (this.mData == null) {
            byte[] byArray2 = new byte[1];
            byArray = byArray2;
            byArray2[0] = 2;
        } else {
            byArray = this.mData;
        }
        return byArray;
    }
}

