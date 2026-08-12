/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.file;

import java.util.ArrayList;

public interface IEbookCallback {
    public static final String TAG = "IEbookCallback";

    public void onFileNames(ArrayList<String> var1);

    public void onProgress(float var1);

    public void onComplete();

    public void onDeleteSuccess(int var1);

    public void onActionResult(int var1);
}

