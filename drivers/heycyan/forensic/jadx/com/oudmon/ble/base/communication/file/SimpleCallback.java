/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.file;

import android.util.Log;
import com.oudmon.ble.base.communication.dfu_temperature.TemperatureEntity;
import com.oudmon.ble.base.communication.dfu_temperature.TemperatureOnceEntity;
import com.oudmon.ble.base.communication.file.ICallback;
import com.oudmon.ble.base.communication.file.PlateEntity;
import java.util.ArrayList;
import java.util.List;

public class SimpleCallback
implements ICallback {
    @Override
    public void onRequestAGPS() {
        Log.i((String)"ICallback", (String)"onRequestAGPS..");
    }

    @Override
    public void onUpdatePlate(List<PlateEntity> array) {
    }

    @Override
    public void onUpdatePlateError(int code) {
    }

    @Override
    public void onDeletePlate() {
    }

    @Override
    public void onDeletePlateError(int code) {
    }

    @Override
    public void onUpdateTemperature(TemperatureEntity data) {
        Log.i((String)"ICallback", (String)"onUpdateTemperature..");
    }

    @Override
    public void onUpdateTemperatureList(List<TemperatureOnceEntity> array) {
        Log.i((String)"ICallback", (String)"onUpdateTemperatureList..");
    }

    @Override
    public void onFileNames(ArrayList<String> fileNames) {
    }

    @Override
    public void onProgress(int percent) {
        Log.i((String)"ICallback", (String)"onProgress..");
    }

    @Override
    public void onComplete() {
        Log.i((String)"ICallback", (String)"onComplete..");
    }

    @Override
    public void onActionResult(int type, int errCode) {
        Log.i((String)"ICallback", (String)"onActionResult..");
    }
}

