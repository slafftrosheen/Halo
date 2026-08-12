/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.file;

import com.oudmon.ble.base.communication.entity.RecordEntity;
import java.util.ArrayList;

public interface IRecordCallback {
    public static final String TAG = "RecordEntity";

    public void onFileNames(ArrayList<RecordEntity> var1);

    public void onProgress(float var1);

    public void onComplete();

    public void onReceiver(byte[] var1);

    public void onActionResult(int var1);
}

