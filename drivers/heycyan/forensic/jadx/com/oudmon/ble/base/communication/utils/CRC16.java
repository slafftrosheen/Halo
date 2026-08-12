/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.utils;

public class CRC16 {
    public static int calcCrc16(byte[] bufData) {
        int CRC = 65535;
        int POLYNOMIAL = 40961;
        if (bufData.length == 0) {
            return CRC;
        }
        for (int i = 0; i < bufData.length; ++i) {
            CRC ^= bufData[i] & 0xFF;
            for (int j = 0; j < 8; ++j) {
                if ((CRC & 1) != 0) {
                    CRC >>= 1;
                    CRC ^= POLYNOMIAL;
                    continue;
                }
                CRC >>= 1;
            }
        }
        return CRC & 0xFFFF;
    }
}

