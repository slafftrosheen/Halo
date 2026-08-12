/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.file;

import com.oudmon.ble.base.communication.dfu_temperature.TemperatureEntity;
import com.oudmon.ble.base.communication.dfu_temperature.TemperatureOnceEntity;
import com.oudmon.ble.base.communication.file.PlateEntity;
import java.util.ArrayList;
import java.util.List;

public interface ICallback {
    public static final String TAG = "ICallback";

    public void onRequestAGPS();

    public void onUpdatePlate(List<PlateEntity> var1);

    public void onUpdatePlateError(int var1);

    public void onDeletePlate();

    public void onDeletePlateError(int var1);

    public void onUpdateTemperature(TemperatureEntity var1);

    public void onUpdateTemperatureList(List<TemperatureOnceEntity> var1);

    public void onFileNames(ArrayList<String> var1);

    public void onProgress(int var1);

    public void onComplete();

    public void onActionResult(int var1, int var2);
}

