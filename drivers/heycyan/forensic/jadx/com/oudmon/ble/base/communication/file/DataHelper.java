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
import com.oudmon.ble.base.communication.file.PlateEntity;
import com.oudmon.qc_utils.bytes.DataTransferUtils;
import com.oudmon.qc_utils.date.DateUtil;
import java.util.ArrayList;
import java.util.List;

public class DataHelper {
    private static final String TAG = "DataHelper";
    private static DataHelper mInstance;

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static DataHelper getInstance() {
        if (mInstance != null) return mInstance;
        Class<DataHelper> clazz = DataHelper.class;
        synchronized (DataHelper.class) {
            if (mInstance != null) return mInstance;
            mInstance = new DataHelper();
            // ** MonitorExit[var0] (shouldn't be in output)
            return mInstance;
        }
    }

    static List<PlateEntity> parsePlate(byte[] data) {
        Log.i((String)TAG, (String)"=========================== Parse Plate Start ============================");
        ArrayList<PlateEntity> mPlateArray = new ArrayList<PlateEntity>();
        byte length = data[0];
        Log.i((String)TAG, (String)("length: " + length));
        try {
            int nameLength;
            for (int index = 1; index < data.length; index += nameLength) {
                int delete = data[index++] & 0xFF;
                nameLength = data[index++] & 0xFF;
                byte[] byteName = new byte[nameLength];
                System.arraycopy(data, index, byteName, 0, nameLength);
                String name = new String(byteName);
                mPlateArray.add(new PlateEntity(delete == 1, name));
            }
            Log.i((String)TAG, (String)("=========================== Parse Plate End ============================" + mPlateArray.size()));
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.i((String)TAG, (String)"=========================== Parse Plate Error ============================");
        }
        return mPlateArray;
    }

    static TemperatureEntity parseTemperature(byte[] data) {
        Log.i((String)TAG, (String)"=========================== ParseTemperature Start ============================");
        TemperatureEntity temperature = new TemperatureEntity();
        try {
            temperature.mIndex = data[0];
            temperature.mTimeSpan = data[1];
            temperature.mValues = new float[data[1] == 0 ? 1 : 1440 / temperature.mTimeSpan];
            int i = 0;
            for (int index = 2; index < data.length; ++index) {
                int temp = data[index] & 0xFF;
                float val = (float)temp * 1.0f / 10.0f;
                temperature.mValues[i++] = val + 20.0f;
            }
            Log.i((String)TAG, (String)("temperature: " + temperature));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Log.i((String)TAG, (String)"=========================== ParseTemperature End ============================");
        return temperature;
    }

    static List<TemperatureOnceEntity> parseTemperatureOnce(byte[] data) {
        Log.i((String)TAG, (String)"=========================== ParseTemperatureOnce Start ============================");
        ArrayList<TemperatureOnceEntity> mArrays = new ArrayList<TemperatureOnceEntity>();
        try {
            DateUtil dateUtil = new DateUtil();
            dateUtil.addDay(-data[0]);
            int index = 1;
            while (index < data.length) {
                TemperatureOnceEntity temperature = new TemperatureOnceEntity();
                temperature.mTime = dateUtil.getZeroTime() + (long)(DataTransferUtils.bytesToShort(data, index) * 60);
                index += 2;
                byte temp = data[index++];
                float val = (float)temp * 1.0f / 10.0f;
                temperature.mValue = val + 20.0f;
                mArrays.add(temperature);
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        Log.i((String)TAG, (String)"=========================== ParseTemperatureOnce End ============================");
        return mArrays;
    }
}

