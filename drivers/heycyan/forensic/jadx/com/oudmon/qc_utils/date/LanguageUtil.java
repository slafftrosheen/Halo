/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.qc_utils.date;

import java.util.Locale;

public class LanguageUtil {
    public static String localLanguage = "nl";

    public static boolean changeDateFormat() {
        return true;
    }

    public static boolean isChina() {
        return false;
    }

    public static boolean isChinaReal() {
        String language = Locale.getDefault().getLanguage();
        return language.contains("zh");
    }
}

