/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ByteUtil {
    protected static final char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static byte[] intToByte(int i, int len) {
        byte[] abyte = null;
        if (len == 1) {
            abyte = new byte[len];
            abyte[0] = (byte)(0xFF & i);
        } else {
            abyte = new byte[len];
            abyte[0] = (byte)(0xFF & i);
            abyte[1] = (byte)((0xFF00 & i) >> 8);
            abyte[2] = (byte)((0xFF0000 & i) >> 16);
            abyte[3] = (byte)((0xFF000000 & i) >> 24);
        }
        return abyte;
    }

    public static float[] bytesToFloats(byte[] bytes, int len, boolean isBe) {
        if (bytes == null) {
            return null;
        }
        float[] floats = new float[len / 4];
        if (isBe) {
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asFloatBuffer().get(floats);
        } else {
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats);
        }
        return floats;
    }

    public static short[] bytesToShorts(byte[] bytes, int len, boolean isBe) {
        if (bytes == null) {
            return null;
        }
        short[] shorts = new short[len / 2];
        if (isBe) {
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(shorts);
        } else {
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(shorts);
        }
        return shorts;
    }

    public static String byteToBit(byte b) {
        return "" + (byte)(b >> 7 & 1) + (byte)(b >> 6 & 1) + (byte)(b >> 5 & 1) + (byte)(b >> 4 & 1) + (byte)(b >> 3 & 1) + (byte)(b >> 2 & 1) + (byte)(b >> 1 & 1) + (byte)(b >> 0 & 1);
    }

    public static int bytes2IntIncludeSignBit(byte[] bytes) {
        int addr = 0;
        if (bytes.length == 1) {
            addr = bytes[0];
        } else if (bytes.length == 4) {
            addr = bytes[3] << 24;
            addr |= bytes[2] << 24 >>> 8;
            addr |= bytes[1] << 24 >>> 16;
            addr |= bytes[0] << 24 >>> 24;
        } else if (bytes.length == 2) {
            addr = bytes[1] << 8;
            addr |= bytes[0] << 24 >>> 24;
        } else if (bytes.length == 3) {
            addr = bytes[2] << 16;
            addr |= bytes[1] << 24 >>> 16;
            addr |= bytes[0] << 24 >>> 24;
        }
        return addr;
    }

    public static byte int2byte(int integer) {
        return (byte)(0xFF & integer);
    }

    public static byte[] getBooleanArray(byte b) {
        byte[] array = new byte[8];
        for (int i = 7; i >= 0; --i) {
            array[i] = (byte)(b & 1);
            b = (byte)(b >> 1);
        }
        return array;
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

    public static int byteToInt(byte bytes) {
        return bytes & 0xFF;
    }

    public static byte[] concat(byte[] a, byte[] b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a, 0, c, 0, a.length);
        System.arraycopy(b, 0, c, a.length, b.length);
        return c;
    }

    public static int bytesToIntForVersion(byte[] bytes) {
        int addr = 0;
        if (bytes.length == 1) {
            addr = bytes[0] & 0xFF;
        } else if (bytes.length == 4) {
            addr = bytes[3] & 0xFF;
            addr |= bytes[2] << 8 & 0xFF00;
            addr |= bytes[1] << 16 & 0xFF0000;
            addr |= bytes[0] << 24 & 0xFF000000;
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

    public static byte[] intToByteBig(int i, int len) {
        byte[] abyte = null;
        if (len == 1) {
            abyte = new byte[len];
            abyte[0] = (byte)(0xFF & i);
        } else {
            abyte = new byte[len];
            abyte[0] = (byte)(i >>> 24 & 0xFF);
            abyte[1] = (byte)(i >>> 16 & 0xFF);
            abyte[2] = (byte)(i >>> 8 & 0xFF);
            abyte[3] = (byte)(i & 0xFF);
        }
        return abyte;
    }

    public static int bytesToIntBig(byte[] bytes) {
        int addr = 0;
        if (bytes.length == 1) {
            addr = bytes[0] & 0xFF;
        } else {
            addr = bytes[0] & 0xFF;
            addr = addr << 8 | bytes[1] & 0xFF;
            addr = addr << 8 | bytes[2] & 0xFF;
            addr = addr << 8 | bytes[3] & 0xFF;
        }
        return addr;
    }

    public static String bytesToString(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder(bytes.length);
        for (byte byteChar : bytes) {
            stringBuilder.append(String.format("%02X", byteChar));
        }
        return stringBuilder.toString();
    }

    public static String bytesToStringFormat(byte[] bytes) {
        StringBuilder stringBuilder = new StringBuilder(bytes.length);
        for (byte byteChar : bytes) {
            stringBuilder.append(String.format("%01X", byteChar));
        }
        return stringBuilder.toString();
    }

    public static int loword(int i) {
        return i & 0xFFFF;
    }

    public static int hiword(int i) {
        return i >>> 8;
    }

    public static String binaryString2hexString(String bString) {
        if (bString == null || bString.equals("") || bString.length() % 8 != 0) {
            return null;
        }
        StringBuffer tmp = new StringBuffer();
        int iTmp = 0;
        for (int i = 0; i < bString.length(); i += 4) {
            iTmp = 0;
            for (int j = 0; j < 4; ++j) {
                iTmp += Integer.parseInt(bString.substring(i + j, i + j + 1)) << 4 - j - 1;
            }
            tmp.append(Integer.toHexString(iTmp));
        }
        return tmp.toString();
    }

    public static String byteArrayToString(byte[] copyOfRange) {
        StringBuilder sb = new StringBuilder();
        for (byte b : copyOfRange) {
            String i = Integer.toHexString(b & 0xFF);
            i = i.length() == 1 ? "0" + i : i;
            sb.append(i);
        }
        return sb.toString();
    }

    public static String byteArraySubToString(byte[] copyOfRange) {
        StringBuilder sb = new StringBuilder();
        for (byte b : copyOfRange) {
            String i = Integer.toHexString(b & 0xFF);
            i = i.length() == 1 ? "0" + i : i;
            sb.append(i);
            if (sb.toString().length() >= 200) break;
        }
        return sb.toString();
    }

    public static byte[] byteToBitArray(int b) {
        byte[] array = new byte[]{(byte)(b >> 7 & 1), (byte)(b >> 6 & 1), (byte)(b >> 5 & 1), (byte)(b >> 4 & 1), (byte)(b >> 3 & 1), (byte)(b >> 2 & 1), (byte)(b >> 1 & 1), (byte)(b >> 0 & 1)};
        return array;
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; ++j) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0xF];
        }
        return new String(hexChars);
    }

    public static String bytesToString1(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        StringBuilder stringBuilder = new StringBuilder(bytes.length);
        String format = "%02X ";
        for (byte byteChar : bytes) {
            stringBuilder.append(String.format(format, byteChar));
        }
        return stringBuilder.toString();
    }

    public static byte[] hexToBytes(String hexStrings) {
        if (hexStrings == null || hexStrings.equals("")) {
            return null;
        }
        String hexString = hexStrings.toLowerCase();
        int length = hexString.length() / 2;
        char[] hexChars = hexString.toCharArray();
        byte[] bytes = new byte[length];
        String hexDigits = "0123456789abcdef";
        for (int i = 0; i < length; ++i) {
            int pos = i * 2;
            int h = hexDigits.indexOf(hexChars[pos]) << 4;
            int l = hexDigits.indexOf(hexChars[pos + 1]);
            if (h == -1 || l == -1) {
                return null;
            }
            bytes[i] = (byte)(h | l);
        }
        return bytes;
    }

    public static String byteAsciiToChar(int ... ascii) {
        String str = "";
        for (int i : ascii) {
            char ch = (char)i;
            str = str + ch;
        }
        return str;
    }

    public static String unicodeByteToStr(byte[] bBuf) {
        StringBuffer result = new StringBuffer();
        Character ch = Character.valueOf('\u0000');
        for (int i = 0; i < bBuf.length && (ch = bBuf[i + 1] == 0 ? Character.valueOf((char)bBuf[i]) : Character.valueOf((char)(bBuf[i] << 8 & 0xFF00 | bBuf[i + 1]))).charValue() != '\u0000'; i += 2) {
            result.append(ch);
        }
        return result.toString();
    }
}

