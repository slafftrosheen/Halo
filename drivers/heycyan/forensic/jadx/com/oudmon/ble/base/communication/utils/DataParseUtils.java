/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.utils;

public class DataParseUtils {
    public static byte[] intToByteArray(int num) {
        int temp = num;
        byte[] b = new byte[4];
        for (int i = 0; i < b.length; ++i) {
            b[i] = Long.valueOf(temp & 0xFF).byteValue();
            temp >>= 8;
        }
        return b;
    }

    public static int byteArrayToInt(byte[] b) {
        int s = 0;
        int s0 = b[0] & 0xFF;
        int s1 = b[1] & 0xFF;
        int s2 = b[2] & 0xFF;
        int s3 = b[3] & 0xFF;
        s = s0 | (s1 <<= 8) | (s2 <<= 16) | (s3 <<= 24);
        return s;
    }
}

