/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.app.ActivityManager
 *  android.app.ActivityManager$RunningAppProcessInfo
 *  android.app.ActivityManager$RunningTaskInfo
 *  android.content.ComponentName
 *  android.content.Context
 */
package com.oudmon.qc_utils.app;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
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
}

