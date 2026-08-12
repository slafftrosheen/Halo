/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.utils;

public class BLEDataFormatUtils {
    public static int BCDToDecimal(byte data) {
        int decade = data >> 4 & 0xF;
        int unit = data & 0xF;
        return decade * 10 + unit;
    }

    public static byte decimalToBCD(int data) {
        int unit = data % 10;
        int decade = data / 10;
        return (byte)(decade << 4 | unit);
    }

    public static int bytes2Int(byte[] data) {
        int length = data.length;
        int res = 0;
        for (int i = 0; i < length; ++i) {
            res |= (data[i] & 0xFF) << 8 * (length - 1 - i);
        }
        return res;
    }
}

