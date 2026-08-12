/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.ActivityManager
 *  android.app.ActivityManager$RunningAppProcessInfo
 *  android.app.ActivityManager$RunningTaskInfo
 *  android.content.ComponentName
 *  android.content.Context
 *  android.content.pm.PackageInfo
 *  android.content.pm.PackageManager
 *  android.os.Build
 *  android.os.Build$VERSION
 */
package com.oudmon.ble.base.util;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.List;

public class AppUtil {
    public static boolean isBackground(Context context) {
        try {
            ActivityManager activityManager = (ActivityManager)context.getSystemService("activity");
            List appProcesses = activityManager.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo appProcess : appProcesses) {
                if (!appProcess.processName.equals(context.getPackageName())) continue;
                return appProcess.importance != 100;
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    public static boolean isApplicationBroughtToBackground(Context context) {
        ComponentName topActivity;
        ActivityManager am = (ActivityManager)context.getSystemService("activity");
        List tasks = am.getRunningTasks(1);
        return !tasks.isEmpty() && !(topActivity = ((ActivityManager.RunningTaskInfo)tasks.get((int)0)).topActivity).getPackageName().equals(context.getPackageName());
    }

    public static String getSystemInfo() {
        return Build.MODEL + "_" + Build.VERSION.SDK_INT + "_" + Build.VERSION.RELEASE;
    }

    public static String getVersionName(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            return packageInfo.versionName;
        }
        catch (Exception e) {
            e.printStackTrace();
            return "1.0.0.0";
        }
    }

    public static String getPackageName(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(context.getPackageName(), 0);
            return packageInfo.packageName;
        }
        catch (Exception e) {
            e.printStackTrace();
            return "com.swatchdevice";
        }
    }
}

