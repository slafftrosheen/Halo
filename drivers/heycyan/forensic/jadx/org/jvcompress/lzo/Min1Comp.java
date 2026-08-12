/*
 * Decompiled with CFR 0.152.
 */
package org.jvcompress.lzo;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import org.jvcompress.lzo.MiniLZO;
import org.jvcompress.util.MInt;

public class Min1Comp {
    private static final int IN_LEN = 0x100000;
    private static final int OUT_LEN = 0x200000;

    private static void clearDict(int[] dict) {
        Arrays.fill(dict, 0);
    }

    private static String R(long millis, long iBytes, long oBytes) {
        return ", millis:" + ++millis + ", MB/sec:" + iBytes * 1000L / millis / 1000000L + ", ratio:" + ++oBytes * 100L / iBytes;
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    public static void main(String[] args) {
        int i;
        long l2;
        MInt OL;
        long l10;
        int r;
        long l1;
        System.out.println("Usage: java [-DDICT=c:/words.txt] org.jvcompress.lzo.Min1Comp");
        int[] dict = new int[131072];
        byte[] in = new byte[0x100000];
        byte[] out = new byte[0x200000];
        MInt outL = new MInt();
        for (int ii = 0; ii < 10; ++ii) {
            Min1Comp.clearDict(dict);
            long l12 = System.currentTimeMillis();
            int r2 = MiniLZO.lzo1x_1_compress(in, 0x100000, out, outL, dict);
            long l102 = System.currentTimeMillis();
            System.out.println(ii + ". Zero Data Compress ret=" + r2 + ", out_lenth:" + outL.v + ",comp-millis:" + Min1Comp.R(l102 - l12, 0x100000L, outL.v) + ", Zero Data Decompressing ...");
            MInt OL2 = new MInt();
            r2 = MiniLZO.lzo1x_decompress(out, outL.v, in, OL2);
            long l22 = System.currentTimeMillis();
            System.out.println(ii + ". Zero Data Got decompressed length:" + OL2.v + ", Zero Data checking ... millis:" + Min1Comp.R(l22 - l102, OL2.v, outL.v));
            for (int i2 = 0; i2 < OL2.v; ++i2) {
                if (in[i2] != 0) {
                    throw new AssertionError((Object)(ii + ". Zero Data Decompreesed values not matching to Zero @:" + i2));
                }
            }
        }
        Random ran = new Random();
        byte[] in_rand = new byte[0x100000];
        int[] partial = new int[]{16, 32, 64, 128, 192, 224, 256};
        for (int j = 0; j < partial.length; ++j) {
            int lim = partial[j];
            for (int ii = 0; ii < 10; ++ii) {
                Min1Comp.clearDict(dict);
                boolean repeatPattern = ii >= 5;
                Min1Comp.fillPartillyRandom(lim, in_rand, ran, repeatPattern);
                System.arraycopy(in_rand, 0, in, 0, in_rand.length);
                l1 = System.currentTimeMillis();
                outL = new MInt();
                r = MiniLZO.lzo1x_1_compress(in, 0x100000, out, outL, dict);
                l10 = System.currentTimeMillis();
                System.out.println(ii + ". Random Data(" + lim + "/repeatPattern:" + repeatPattern + ") Compress ret=" + r + ", outL:" + outL.v + ", millis:" + Min1Comp.R(l10 - l1, 0x100000L, outL.v));
                OL = new MInt();
                r = MiniLZO.lzo1x_decompress(out, outL.v, in, OL);
                l2 = System.currentTimeMillis();
                System.out.println(ii + ". Random Data(" + lim + "/repeatPattern:" + repeatPattern + ")Got decompressed length:" + OL.v + ",millis:" + Min1Comp.R(l2 - l10, OL.v, outL.v));
                for (i = 0; i < OL.v; ++i) {
                    if (in[i] != in_rand[i]) {
                        throw new AssertionError((Object)(ii + ". Random Data(" + lim + "/repeatPattern:" + repeatPattern + ")  Decompreesed values not matching to Zero @:" + i));
                    }
                }
            }
        }
        BufferedInputStream fis = null;
        try {
            int read;
            String file = System.getProperty("DICT", "c:/words.txt");
            fis = new BufferedInputStream(new FileInputStream(file));
            block20: while ((read = fis.read(in_rand)) >= 0) {
                int ii = 0;
                while (true) {
                    if (ii >= 10) continue block20;
                    Min1Comp.clearDict(dict);
                    System.arraycopy(in_rand, 0, in, 0, read);
                    outL = new MInt();
                    l1 = System.currentTimeMillis();
                    r = MiniLZO.lzo1x_1_compress(in, read, out, outL, dict);
                    l10 = System.currentTimeMillis();
                    System.out.println(ii + ". Dict-File Data(" + read + ") Compress ret=" + r + ", outL:" + outL.v + ", millis:" + Min1Comp.R(l10 - l1, read, outL.v));
                    OL = new MInt();
                    r = MiniLZO.lzo1x_decompress(out, outL.v, in, OL);
                    l2 = System.currentTimeMillis();
                    System.out.println(ii + ". Dict-File Data(" + read + ")Got decompressed length:" + OL.v + ", millis:" + Min1Comp.R(l2 - l10, OL.v, outL.v));
                    if (OL.v != read) {
                        System.err.println("Dict-File Decompressed length does not match");
                    }
                    for (i = 0; i < OL.v; ++i) {
                        if (in[i] != in_rand[i]) {
                            throw new AssertionError((Object)(ii + ". Dict-File Data(" + read + ")  Decompreesed values not matching to Zero @:" + i));
                        }
                    }
                    ++ii;
                }
                break;
            }
        }
        catch (FileNotFoundException file) {
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            }
            catch (IOException iOException) {}
        }
    }

    private static void fillPartillyRandom(int lim, byte[] in, Random ran, boolean rpt) {
        int i = 0;
        do {
            int repeat = rpt ? ran.nextInt(10) : 1;
            byte b = (byte)ran.nextInt(lim);
            for (int j = 0; j < repeat && i < in.length; ++j) {
                in[i++] = b;
            }
        } while (i < in.length);
    }
}

