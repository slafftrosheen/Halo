/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.util.Log
 */
package com.oudmon.ble.base.communication;

import android.util.Log;
import java.util.Arrays;
import org.jvcompress.lzo.MiniLZO;
import org.jvcompress.util.MInt;

public class CompressUtils {
    private static final String TAG = "Jxr35";

    public static byte[] compress(byte[] input) {
        try {
            byte[] output = new byte[input.length + input.length / 16 + 64 + 3];
            int[] dict = new int[65536];
            MInt mInt = new MInt();
            MiniLZO.lzo1x_1_compress(input, input.length, output, mInt, dict);
            byte[] compress = Arrays.copyOfRange(output, 0, mInt.v);
            return compress;
        }
        catch (Exception e) {
            e.printStackTrace();
            Log.i((String)TAG, (String)"compress.. FileNotFoundException");
            return new byte[0];
        }
    }
}

