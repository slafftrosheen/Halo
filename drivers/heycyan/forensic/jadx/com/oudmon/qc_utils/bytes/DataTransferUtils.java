/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.qc_utils.bytes;

public class DataTransferUtils {
    public static String getHexString(byte[] data) {
        StringBuilder s = new StringBuilder();
        if (data != null) {
            for (byte aData : data) {
                String hex = Integer.toHexString(aData & 0xFF);
                if (hex.length() == 1) {
                    hex = '0' + hex;
                }
                s.append(hex);
            }
        }
        return s.toString();
    }

    public static byte[] intToBytes(int value) {
        byte[] src = new byte[4];
        src[3] = (byte)(value >> 24 & 0xFF);
        src[2] = (byte)(value >> 16 & 0xFF);
        src[1] = (byte)(value >> 8 & 0xFF);
        src[0] = (byte)(value & 0xFF);
        return src;
    }

    public static int bytesToInt(byte[] src, int offset) {
        return src[offset] & 0xFF | (src[offset + 1] & 0xFF) << 8 | (src[offset + 2] & 0xFF) << 16 | (src[offset + 3] & 0xFF) << 24;
    }

    public static int byte2Int(byte[] src, int offset) {
        return src[offset] & 0xFF | (src[offset + 1] & 0xFF) << 8;
    }

    public static short bytesToShort(byte[] src, int offset) {
        return (short)(src[offset] & 0xFF | (src[offset + 1] & 0xFF) << 8);
    }

    public static byte[] shortToBytes(short value) {
        byte[] src = new byte[2];
        src[1] = (byte)(value >> 8 & 0xFF);
        src[0] = (byte)(value & 0xFF);
        return src;
    }

    public static int arrays2Int(byte[] data) {
        if (data.length == 1) {
            return 0xFF & data[0];
        }
        if (data.length == 2) {
            return DataTransferUtils.bytesToInt(data);
        }
        if (data.length == 4) {
            return DataTransferUtils.bytesToInt(data, 0);
        }
        return -1;
    }

    public static int bytesToInt(byte[] bytes) {
        int addr = 0;
        if (bytes.length == 1) {
            addr = bytes[0] & 0xFF;
        } else if (bytes.length == 4) {
            addr = bytes[0] & 0xFF;
            addr |= bytes[1] << 8 & 0xFF00;
            addr |= bytes[2] << 16 & 0xFF0000;
            addr |= bytes[3] << 24 & 0xFF000000;
        } else if (bytes.length == 2) {
            addr = bytes[0] & 0xFF;
            addr |= bytes[1] << 8 & 0xFF00;
        } else if (bytes.length == 3) {
            addr = bytes[0] & 0xFF;
            addr |= bytes[1] << 8 & 0xFF00;
            addr |= bytes[2] << 16 & 0xFF0000;
        }
        return addr;
    }

    public static float bytes2Float(byte[] data, int offset) {
        return Float.intBitsToFloat(DataTransferUtils.bytesToInt(data, offset));
    }

    public static int enableWeek(int week) {
        return week & 0x80;
    }
}

