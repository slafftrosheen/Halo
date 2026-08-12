/*
 * Decompiled with CFR 0.152.
 */
package org.jvcompress.lzo;

import java.io.File;
import java.io.FileInputStream;
import org.jvcompress.lzo.LZOConstants;
import org.jvcompress.util.MInt;

public final class MiniLZO
implements LZOConstants {
    public static final int c_top_loop = 1;
    public static final int c_first_literal_run = 2;
    public static final int c_match = 3;
    public static final int c_copy_match = 4;
    public static final int c_match_done = 5;
    public static final int c_match_next = 6;
    public static final int c_eof_found = 7;
    public static final int c_input_overrun = 8;
    public static final int c_output_overrun = 9;
    public static final int c_lookbehind_overrun = 10;
    public static final int c0_top = 1;
    public static final int c0_try_match = 2;
    public static final int c0_literal = 3;
    public static final int c0_match = 4;
    public static final int c0_m3_m4_len = 5;
    public static final int c0_m3_m4_offset = 6;
    public static final int c0_last = 7;
    private static final boolean debug = false;

    private static final int U(byte b) {
        return b & 0xFF;
    }

    public static void main(String[] args) {
        int ZERO_FILL = Integer.getInteger("ZERO_FILL", 0);
        String IFILE = System.getProperty("IFILE", "IFILE");
        String OFILE = System.getProperty("OFILE", "IFILE");
        try {
            File fComp_ = new File(OFILE);
            long len = fComp_.length();
            byte[] buf = new byte[(int)len];
            File fUnComp_ = new File(IFILE);
            byte[] out = new byte[(int)fUnComp_.length()];
            byte[] out_ori = new byte[(int)fUnComp_.length()];
            FileInputStream fComp = new FileInputStream(fComp_);
            FileInputStream fUnComp = new FileInputStream(IFILE);
            int ret = fComp.read(buf);
            if (ret > 0) {
                MInt out_len = new MInt();
                System.out.println("Decompressing byte.length=" + len);
                int r = MiniLZO.lzo1x_decompress(buf, (int)len, out, out_len);
                System.out.println("Got decompressed length:" + out_len.v);
                if (ZERO_FILL > 0) {
                    System.out.println("Doing zero fill check");
                    for (int i = 0; i < out_len.v; ++i) {
                        if (out[i] != 0) {
                            throw new AssertionError((Object)("Decompreesed values not matching to Zero @:" + i));
                        }
                    }
                } else {
                    ret = fUnComp.read(out_ori);
                    for (int i = 0; i < out_len.v; ++i) {
                        if (out[i] != out_ori[i]) {
                            throw new AssertionError((Object)("Decompreesed values not matching to Zero @:" + i));
                        }
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * Unable to fully structure code
     */
    private static final int _lzo1x_1_do_compress(byte[] in, int in_len, byte[] out, MInt out_len, int[] dict) {
        ip = 0;
        in_base = 0;
        out_base = 0;
        in_end = in_base + in_len;
        ip_end = in_base + in_len - 8 - 5;
        ii = 0;
        state = 1;
        op = out_base;
        ii = ip = in_base;
        ip += 4;
        m_pos = in_base;
        m_off = in_base;
        m_len = 0;
        dindex = 0;
        block9: while (true) {
            switch (state) {
                case 1: {
                    dindex = (33 * (((in[ip + 1 + 2 + 1] & 255) << 6 ^ (in[ip + 1 + 1 + 1] & 255) << 5 ^ (in[ip + 1 + 0] & 255) << 5 ^ in[ip + 0] & 255) >> 5) & 16383) << 0;
                    m_pos = dict[dindex];
                    m_pos = ip - (ip - m_pos);
                    if (m_pos <= in_base || (m_off = ip - m_pos) <= 0 || m_off > 49151) {
                        state = 3;
                        continue block9;
                    }
                    if (m_off <= 2048 || in[m_pos + 3] == in[ip + 3]) {
                        state = 2;
                        continue block9;
                    }
                    m_pos = dict[dindex = dindex & 2047 ^ 8223];
                    if (m_pos < in_base || (m_off = ip - m_pos) <= 0 || m_off > 49151) {
                        state = 3;
                        continue block9;
                    }
                    if (m_off <= 2048 || in[m_pos + 3] == in[ip + 3]) {
                        state = 2;
                        continue block9;
                    }
                    state = 3;
                    continue block9;
                }
                case 2: {
                    if (in[m_pos] == in[ip] && in[m_pos + 1] == in[ip + 1] && in[m_pos + 2] == in[ip + 2]) {
                        state = 4;
                        continue block9;
                    }
                }
                case 3: {
                    dict[dindex] = ip++;
                    if (ip >= ip_end) break block9;
                    state = 1;
                    continue block9;
                }
                case 4: {
                    dict[dindex] = ip;
                    if (ip - ii > 0) {
                        t = ip - ii;
                        if (t <= 3) {
                            v0 = op - 2;
                            out[v0] = (byte)(out[v0] | (byte)t);
                        } else if (t <= 18) {
                            out[op++] = (byte)(t - 3);
                        } else {
                            out[op++] = 0;
                            for (tt = t - 18; tt > 255; tt -= 255) {
                                out[op++] = 0;
                            }
                            out[op++] = (byte)tt;
                        }
                        do {
                            out[op++] = in[ii++];
                        } while (--t > 0);
                    }
                    ip += 3;
                    if (in[m_pos + 3] != in[ip++] || in[m_pos + 4] != in[ip++] || in[m_pos + 5] != in[ip++] || in[m_pos + 6] != in[ip++] || in[m_pos + 7] != in[ip++] || in[m_pos + 8] != in[ip++]) {
                        m_len = --ip - ii;
                        if (m_off > 2048) {
                            if (m_off <= 16384) {
                                --m_off;
                                out[op++] = (byte)(32 | m_len - 2);
                                state = 6;
                                continue block9;
                            }
                            out[op++] = (byte)(16 | ((m_off -= 16384) & 16384) >> 11 | m_len - 2);
                            state = 6;
                            continue block9;
                        }
                        out[op++] = (byte)(m_len - 1 << 5 | (--m_off & 7) << 2);
                        out[op++] = (byte)(m_off >> 3);
                        state = 7;
                        continue block9;
                    }
                    end = in_end;
                    m = m_pos + 8 + 1;
                    while (ip < end && in[m] == in[ip]) {
                        ++m;
                        ++ip;
                    }
                    m_len = ip - ii;
                    if (m_off > 16384) ** GOTO lbl96
                    --m_off;
                    if (m_len <= 33) {
                        out[op++] = (byte)(32 | m_len - 2);
                    } else {
                        m_len -= 33;
                        out[op++] = 32;
                        state = 5;
                        continue block9;
lbl96:
                        // 1 sources

                        m_off -= 16384;
                        if (m_len <= 9) {
                            out[op++] = (byte)(16 | (m_off & 16384) >> 11 | m_len - 2);
                        } else {
                            m_len -= 9;
                            out[op++] = (byte)(16 | (m_off & 16384) >> 11);
                            while (m_len > 255) {
                                m_len -= 255;
                                out[op++] = 0;
                            }
                            out[op++] = (byte)m_len;
                        }
                    }
                    out[op++] = (byte)((m_off & 63) << 2);
                    out[op++] = (byte)(m_off >> 6);
                    state = 7;
                    continue block9;
                }
                case 5: {
                    while (m_len > 255) {
                        m_len -= 255;
                        out[op++] = 0;
                    }
                    out[op++] = (byte)m_len;
                }
                case 6: {
                    out[op++] = (byte)((m_off & 63) << 2);
                    out[op++] = (byte)(m_off >> 6);
                }
                case 7: {
                    ii = ip;
                    if (ip >= ip_end) break block9;
                    state = 1;
                }
                default: {
                    continue block9;
                }
            }
            break;
        }
        out_len.v = op - out_base;
        return in_end - ii;
    }

    public static final int lzo1x_1_compress(byte[] in, int in_len, byte[] out, MInt out_len, int[] dict) {
        int in_base = 0;
        int out_base = 0;
        int op = 0;
        int t = 0;
        if (in_len <= 13) {
            t = in_len;
        } else {
            t = MiniLZO._lzo1x_1_do_compress(in, in_len, out, out_len, dict);
            op += out_len.v;
        }
        if (t > 0) {
            int ii = in_base + in_len - t;
            if (op == out_base && t <= 238) {
                out[op++] = (byte)(17 + t);
            } else if (t <= 3) {
                int n = op - 2;
                out[n] = (byte)(out[n] | (byte)t);
            } else if (t <= 18) {
                out[op++] = (byte)(t - 3);
            } else {
                int tt;
                out[op++] = 0;
                for (tt = t - 18; tt > 255; tt -= 255) {
                    out[op++] = 0;
                }
                out[op++] = (byte)tt;
            }
            do {
                out[op++] = in[ii++];
            } while (--t > 0);
        }
        out[op++] = 17;
        out[op++] = 0;
        out[op++] = 0;
        out_len.v = op - out_base;
        return 0;
    }

    /*
     * Unable to fully structure code
     */
    public static final int lzo1x_decompress(byte[] in, int in_len, byte[] out, MInt out_len) {
        op = 0;
        ip = 0;
        state = 1;
        max = 0;
        diff = 0;
        min = 0;
        m_pos = 0;
        ip_end = in_len;
        out_len.v = 0;
        t = in[ip] & 255;
        if (t > 17) {
            ++ip;
            if ((t -= 17) < 4) {
                state = 6;
            } else {
                do {
                    out[op++] = in[ip++];
                } while (--t > 0);
                state = 2;
            }
        }
        block9: while (true) {
            if_block = false;
            switch (state) {
                case 1: {
                    t = in[ip++] & 255;
                    if (t >= 16) {
                        state = 3;
                        continue block9;
                    }
                    if (t == 0) {
                        while (in[ip] == 0) {
                            t += 255;
                            ++ip;
                        }
                        t += 15 + (in[ip++] & 255);
                    }
                    out[op] = in[ip];
                    out[op + 1] = in[ip + 1];
                    out[op + 2] = in[ip + 2];
                    out[op + 3] = in[ip + 3];
                    op += 4;
                    ip += 4;
                    if (--t <= 0) ** GOTO lbl58
                    if (t < 4) ** GOTO lbl55
                    do {
                        out[op] = in[ip];
                        out[op + 1] = in[ip + 1];
                        out[op + 2] = in[ip + 2];
                        out[op + 3] = in[ip + 3];
                        op += 4;
                        ip += 4;
                    } while ((t -= 4) >= 4);
                    if (t > 0) {
                        do {
                            out[op++] = in[ip++];
                        } while (--t > 0);
                    }
                    ** GOTO lbl58
lbl55:
                    // 1 sources

                    do {
                        out[op++] = in[ip++];
                    } while (--t > 0);
                }
lbl58:
                // 4 sources

                case 2: {
                    t = in[ip++] & 255;
                    if (t >= 16) {
                        state = 3;
                        continue block9;
                    }
                    if ((diff = Math.abs((m_pos = op - 2049 - (t >> 2) - ((in[ip++] & 255) << 2)) - op)) > max) {
                        max = diff;
                    }
                    if ((diff = m_pos - op) < min) {
                        min = diff;
                    }
                    out[op++] = out[m_pos++];
                    out[op++] = out[m_pos++];
                    out[op++] = out[m_pos];
                    state = 5;
                    continue block9;
                }
                case 3: {
                    if (t >= 64) {
                        m_pos = op - 1;
                        m_pos -= t >> 2 & 7;
                        if ((diff = Math.abs((m_pos -= (in[ip++] & 255) << 3) - op)) > max) {
                            max = diff;
                        }
                        if ((diff = m_pos - op) < min) {
                            min = diff;
                        }
                        t = (t >> 5) - 1;
                        state = 4;
                        continue block9;
                    }
                    if (t >= 32) {
                        if ((t &= 31) == 0) {
                            while (in[ip] == 0) {
                                t += 255;
                                ++ip;
                            }
                            t += 31 + (in[ip++] & 255);
                        }
                        m_pos = op - 1;
                        diff = Math.abs((m_pos -= (in[ip] & 255) + ((in[ip + 1] & 255) << 8) >> 2) - op);
                        if (diff > max) {
                            max = diff;
                        }
                        if ((diff = m_pos - op) < min) {
                            min = diff;
                        }
                        ip += 2;
                    } else if (t >= 16) {
                        m_pos = op;
                        diff = Math.abs((m_pos -= (t & 8) << 11) - op);
                        if (diff > max) {
                            max = diff;
                        }
                        if ((diff = m_pos - op) < min) {
                            min = diff;
                        }
                        if ((t &= 7) == 0) {
                            while (in[ip] == 0) {
                                t += 255;
                                ++ip;
                            }
                            t += 7 + (in[ip++] & 255);
                        }
                        if ((diff = Math.abs((m_pos -= (in[ip] & 255) + ((in[ip + 1] & 255) << 8) >> 2) - op)) > max) {
                            max = diff;
                        }
                        if ((diff = m_pos - op) < min) {
                            min = diff;
                        }
                        ip += 2;
                        if (m_pos == op) break block9;
                        m_pos -= 16384;
                    } else {
                        m_pos = op - 1;
                        m_pos -= t >> 2;
                        if ((diff = Math.abs((m_pos -= (in[ip++] & 255) << 2) - op)) > max) {
                            max = diff;
                        }
                        if ((diff = m_pos - op) < min) {
                            min = diff;
                        }
                        out[op++] = out[m_pos++];
                        out[op++] = out[m_pos];
                        state = 5;
                        continue block9;
                    }
                    if (t >= 6 && op - m_pos >= 4) {
                        if_block = true;
                        out[op] = out[m_pos];
                        out[op + 1] = out[m_pos + 1];
                        out[op + 2] = out[m_pos + 2];
                        out[op + 3] = out[m_pos + 3];
                        op += 4;
                        m_pos += 4;
                        t -= 2;
                        do {
                            out[op] = out[m_pos];
                            out[op + 1] = out[m_pos + 1];
                            out[op + 2] = out[m_pos + 2];
                            out[op + 3] = out[m_pos + 3];
                            op += 4;
                            m_pos += 4;
                        } while ((t -= 4) >= 4);
                        if (t > 0) {
                            do {
                                out[op++] = out[m_pos++];
                            } while (--t > 0);
                        }
                    }
                }
                case 4: {
                    if (!if_block) {
                        out[op++] = out[m_pos++];
                        out[op++] = out[m_pos++];
                        do {
                            out[op++] = out[m_pos++];
                        } while (--t > 0);
                    }
                }
                case 5: {
                    t = in[ip - 2] & 255 & 3;
                    if (t == 0) {
                        state = 1;
                        continue block9;
                    }
                }
                case 6: {
                    out[op++] = in[ip++];
                    if (t > 1) {
                        out[op++] = in[ip++];
                        if (t > 2) {
                            out[op++] = in[ip++];
                        }
                    }
                    t = in[ip++] & 255;
                    state = 3;
                    continue block9;
                }
                default: {
                    continue block9;
                }
            }
            break;
        }
        out_len.v = op;
        return ip == in.length ? 0 : (ip < in.length ? -8 : -4);
    }

    /*
     * Unable to fully structure code
     */
    public static final int lzo1x_decompress_safe(byte[] in, int in_len, byte[] out, MInt out_len) {
        op = 0;
        ip = 0;
        t = 0;
        m_pos = 0;
        state = 1;
        out_len.v = 0;
        t = in[ip] & 255;
        if (t > 17) {
            ++ip;
            if ((t -= 17) < 4) {
                state = 6;
            } else if (out.length < t) {
                state = 9;
            } else if (in.length < t + 1) {
                state = 8;
            } else {
                do {
                    out[op++] = in[ip++];
                } while (--t > 0);
                state = 2;
            }
        }
        out_base = false;
        ip_end = in.length;
        op_end = out.length;
        block15: while (ip < ip_end) {
            if_block = false;
            switch (state) {
                default: {
                    break block15;
                }
                case 1: {
                    t = in[ip++] & 255;
                    if (t >= 16) {
                        state = 3;
                        continue block15;
                    }
                    if (t == 0) {
                        if (ip_end - ip < 1) {
                            state = 8;
                            continue block15;
                        }
                        while (in[ip] == 0) {
                            t += 255;
                            if (ip_end - ++ip >= 1) continue;
                            state = 8;
                            continue block15;
                        }
                        t += 15 + (in[ip++] & 255);
                    }
                    if (op_end - op < t + 3) {
                        state = 9;
                        continue block15;
                    }
                    if (ip_end - ip < t + 4) {
                        state = 8;
                        continue block15;
                    }
                    out[op] = in[ip];
                    out[op + 1] = in[ip + 1];
                    out[op + 2] = in[ip + 2];
                    out[op + 3] = in[ip + 3];
                    op += 4;
                    ip += 4;
                    if (--t <= 0) ** GOTO lbl76
                    if (t < 4) ** GOTO lbl73
                    do {
                        out[op] = in[ip];
                        out[op + 1] = in[ip + 1];
                        out[op + 2] = in[ip + 2];
                        out[op + 3] = in[ip + 3];
                        op += 4;
                        ip += 4;
                    } while (t >= 4);
                    if (t > 0) {
                        do {
                            out[op++] = in[ip++];
                        } while (--t > 0);
                    }
                    ** GOTO lbl76
lbl73:
                    // 1 sources

                    do {
                        out[op++] = in[ip++];
                    } while (--t > 0);
                }
lbl76:
                // 4 sources

                case 2: {
                    t = in[ip++] & 255;
                    if (t >= 16) {
                        state = 3;
                        continue block15;
                    }
                    if ((m_pos = op - 2049 - (t >> 2) - ((in[ip++] & 255) << 2)) < 0 || m_pos >= op) {
                        state = 10;
                        continue block15;
                    }
                    if (op_end - op < 3) {
                        state = 9;
                        continue block15;
                    }
                    out[op++] = out[m_pos++];
                    out[op++] = out[m_pos++];
                    out[op++] = out[m_pos];
                    state = 5;
                    continue block15;
                }
                case 3: {
                    if (t >= 64) {
                        m_pos = op - 1;
                        m_pos -= t >> 2 & 7;
                        v0 = ip++;
                        t = (t >> 5) - 1;
                        if ((m_pos -= (in[v0] & 255) << 3) < 0 || m_pos >= op) {
                            state = 10;
                            continue block15;
                        }
                        if (op_end - op < t + 3 - 1) {
                            state = 9;
                            continue block15;
                        }
                        state = 5;
                        continue block15;
                    }
                    if (t >= 32) {
                        if ((t &= 31) == 0) {
                            if (ip_end - ip < 1) {
                                state = 8;
                                continue block15;
                            }
                            while (in[ip] == 0) {
                                t += 255;
                                if (ip_end - ++ip >= 1) continue;
                                state = 8;
                                continue block15;
                            }
                            t += 31 + (in[ip++] & 255);
                        }
                        m_pos = op - 1;
                        m_pos -= (in[ip] & 255) + ((in[ip + 1] & 255) << 8) >> 2;
                        ip += 2;
                    } else if (t >= 16) {
                        m_pos = op;
                        m_pos -= (t & 8) << 11;
                        if ((t &= 7) == 0) {
                            if (ip_end - ip < 1) {
                                state = 8;
                                continue block15;
                            }
                            while (in[ip] == 0) {
                                t += 255;
                                if (ip_end - ++ip >= 1) continue;
                                state = 8;
                                continue block15;
                            }
                            t += 7 + (in[ip++] & 255);
                        }
                        if ((m_pos -= (in[ip += 2] & 255) + ((in[ip + 1] & 255) << 8) >> 2) == op) {
                            state = 7;
                            break block15;
                        }
                        m_pos -= 16384;
                    } else {
                        m_pos = op - 1;
                        m_pos -= t >> 2;
                        if ((m_pos -= (in[ip++] & 255) << 2) < 0 || m_pos >= op) {
                            state = 10;
                            continue block15;
                        }
                        if (op_end - op < 2) {
                            state = 9;
                            continue block15;
                        }
                        out[op++] = out[m_pos++];
                        out[op++] = out[m_pos];
                        state = 5;
                        continue block15;
                    }
                    if (m_pos < 0 || m_pos >= op) {
                        state = 10;
                        continue block15;
                    }
                    if (op_end - op < t + 3 - 1) {
                        state = 9;
                        continue block15;
                    }
                    if (t >= 6 && op - m_pos >= 4) {
                        if_block = true;
                        out[op] = out[m_pos];
                        out[op + 1] = out[m_pos + 1];
                        out[op + 2] = out[m_pos + 2];
                        out[op + 3] = out[m_pos + 3];
                        op += 4;
                        m_pos += 4;
                        t -= 2;
                        do {
                            out[op] = out[m_pos];
                            out[op + 1] = out[m_pos + 1];
                            out[op + 2] = out[m_pos + 2];
                            out[op + 3] = out[m_pos + 3];
                            op += 4;
                            m_pos += 4;
                        } while ((t -= 4) >= 4);
                        if (t > 0) {
                            do {
                                out[op++] = out[m_pos++];
                            } while (--t > 0);
                        }
                    }
                }
                case 4: {
                    if (!if_block) {
                        out[op++] = out[m_pos++];
                        out[op++] = out[m_pos++];
                        do {
                            out[op++] = out[m_pos++];
                        } while (--t > 0);
                    }
                }
                case 5: {
                    t = in[ip - 2] & 3;
                    if (t == 0) {
                        state = 1;
                        continue block15;
                    }
                }
                case 6: {
                    if (op_end - op < t) {
                        state = 9;
                        continue block15;
                    }
                    if (ip_end - ip < t + 1) {
                        state = 8;
                        continue block15;
                    }
                    out[op++] = in[ip++];
                    if (t > 1) {
                        out[op++] = in[ip++];
                        if (t > 2) {
                            out[op++] = in[ip++];
                        }
                    }
                    t = in[ip++] & 255;
                    if (ip < ip_end) {
                        state = 3;
                        continue block15;
                    }
                    state = 1;
                    continue block15;
                }
            }
        }
        out_len.v = op - 0;
        switch (state) {
            case 7: {
                return ip == ip_end ? 0 : (ip < ip_end ? -8 : -4);
            }
            case 8: {
                return -4;
            }
            case 9: {
                return -5;
            }
            case 10: {
                return -6;
            }
        }
        return -7;
    }
}

