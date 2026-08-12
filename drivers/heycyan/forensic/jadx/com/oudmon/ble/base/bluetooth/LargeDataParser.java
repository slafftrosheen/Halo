/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.bluetooth;

import com.oudmon.ble.base.communication.ILargeDataResponse;
import com.oudmon.ble.base.communication.LargeDataHandler;
import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;
import com.oudmon.ble.base.communication.bigData.resp.BigDataBeanFactory;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.util.Arrays;

public class LargeDataParser {
    private static LargeDataParser instance;
    public static String uuid_notify;
    public byte[] tempData;
    public int dataLength = 0;
    boolean end = true;

    private LargeDataParser() {
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    public static LargeDataParser getInstance() {
        if (instance != null) return instance;
        Class<LargeDataParser> clazz = LargeDataParser.class;
        synchronized (LargeDataParser.class) {
            if (instance != null) return instance;
            instance = new LargeDataParser();
            // ** MonitorExit[var0] (shouldn't be in output)
            return instance;
        }
    }

    public void parseBigLargeData(String uuid, byte[] value) {
        if (uuid_notify.equals(uuid)) {
            if (value.length >= 6 && (value[0] & 0xFF) == 188 && this.end) {
                this.dataLength = ByteUtil.bytesToInt(Arrays.copyOfRange(value, 2, 4));
                if (value.length - 6 >= this.dataLength) {
                    this.tempData = Arrays.copyOfRange(value, 0, value.length);
                    this.parseData(this.tempData);
                    this.tempData = new byte[0];
                    this.end = true;
                } else {
                    this.end = false;
                    this.tempData = Arrays.copyOfRange(value, 0, value.length);
                }
            } else {
                this.tempData = ByteUtil.concat(this.tempData, value);
                if (this.tempData.length - 6 == this.dataLength) {
                    this.end = true;
                    this.parseData(this.tempData);
                    this.tempData = new byte[0];
                } else {
                    this.end = false;
                }
            }
        }
    }

    public void parseData(byte[] data) {
        BaseResponse response1;
        byte notifyKey = data[1];
        if ((data[0] & 0xFF) == 188 && (response1 = BigDataBeanFactory.createBean(notifyKey)) != null) {
            response1.acceptData(data);
            ILargeDataResponse response = data[1] == 115 ? LargeDataHandler.getInstance().getNoClearMap().get(notifyKey) : LargeDataHandler.getInstance().getRespMap().get(notifyKey);
            if (response != null) {
                response.parseData(notifyKey, response1);
            }
        }
    }

    static {
        uuid_notify = "de5bf729-d711-4e47-af26-65e3012a5dc7";
    }
}

