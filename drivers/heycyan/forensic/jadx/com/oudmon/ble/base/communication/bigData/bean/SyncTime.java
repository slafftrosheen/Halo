/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication.bigData.bean;

import android.util.Log;
import com.oudmon.ble.base.communication.utils.BLEDataFormatUtils;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class SyncTime {
    private byte mLanguage = 0;
    private byte[] mData = new byte[9];
    private Map<String, Integer> mLocaleMap = new HashMap<String, Integer>();

    public SyncTime(int offset) {
        this.initMap();
        this.setLanguage();
        Calendar calendar = Calendar.getInstance();
        calendar.add(13, offset);
        this.mData[0] = BLEDataFormatUtils.decimalToBCD(calendar.get(1) % 2000);
        this.mData[1] = BLEDataFormatUtils.decimalToBCD(calendar.get(2) + 1);
        this.mData[2] = BLEDataFormatUtils.decimalToBCD(calendar.get(5));
        this.mData[3] = BLEDataFormatUtils.decimalToBCD(calendar.get(11));
        this.mData[4] = BLEDataFormatUtils.decimalToBCD(calendar.get(12));
        this.mData[5] = BLEDataFormatUtils.decimalToBCD(calendar.get(13));
    }

    private void initMap() {
        this.mLocaleMap.put("zh_CN", 0);
        this.mLocaleMap.put("en", 1);
        this.mLocaleMap.put("zh_HK", 2);
        this.mLocaleMap.put("zh_TW", 2);
        this.mLocaleMap.put("el", 3);
        this.mLocaleMap.put("fr", 4);
        this.mLocaleMap.put("de", 5);
        this.mLocaleMap.put("it", 6);
        this.mLocaleMap.put("es", 7);
        this.mLocaleMap.put("nl", 8);
        this.mLocaleMap.put("pt", 9);
        this.mLocaleMap.put("ru", 10);
        this.mLocaleMap.put("tr", 11);
        this.mLocaleMap.put("ja", 12);
        this.mLocaleMap.put("ko", 13);
        this.mLocaleMap.put("pl", 14);
        this.mLocaleMap.put("ro", 15);
        this.mLocaleMap.put("ar", 16);
        this.mLocaleMap.put("th", 17);
        this.mLocaleMap.put("vi", 18);
        this.mLocaleMap.put("in", 19);
        this.mLocaleMap.put("hi", 20);
        this.mLocaleMap.put("cs", 21);
        this.mLocaleMap.put("sk", 22);
        this.mLocaleMap.put("hu", 23);
        this.mLocaleMap.put("iw", 24);
        this.mLocaleMap.put("hr", 25);
        this.mLocaleMap.put("sl", 26);
    }

    public byte[] getSubData() {
        this.mData[6] = this.mLanguage;
        float timeZone = SyncTime.getTimeZone();
        timeZone = (24.0f + timeZone) % 24.0f;
        this.mData[7] = (byte)(timeZone * 2.0f + 1.0f);
        this.mData[8] = 1;
        return this.mData;
    }

    public static float getTimeZone() {
        return (float)TimeZone.getDefault().getOffset(System.currentTimeMillis()) * 1.0f / 3600000.0f;
    }

    public void setLanguage() {
        Integer value;
        String language = Locale.getDefault().getLanguage();
        if (language.startsWith("zh")) {
            language = Locale.getDefault().getLanguage() + "_" + Locale.getDefault().getCountry().toUpperCase();
        }
        int result = (value = this.mLocaleMap.get(language)) == null ? 1 : value;
        Log.i((String)"GLASSES_LOG", (String)("SetTimeReq -> mLanguage: " + language + ", value: " + value + ", result: " + result));
        this.mLanguage = (byte)result;
    }
}

