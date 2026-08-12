/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.bluetooth;

import com.oudmon.ble.base.communication.rsp.AppRevisionResp;
import com.oudmon.ble.base.communication.rsp.AppSportRsp;
import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.rsp.BatteryRsp;
import com.oudmon.ble.base.communication.rsp.BloodOxygenSettingRsp;
import com.oudmon.ble.base.communication.rsp.BloodSugarLipidsSettingRsp;
import com.oudmon.ble.base.communication.rsp.BpDataRsp;
import com.oudmon.ble.base.communication.rsp.BpSettingRsp;
import com.oudmon.ble.base.communication.rsp.CameraNotifyRsp;
import com.oudmon.ble.base.communication.rsp.DegreeSwitchRsp;
import com.oudmon.ble.base.communication.rsp.DeviceAvatarRsp;
import com.oudmon.ble.base.communication.rsp.DeviceNotifyRsp;
import com.oudmon.ble.base.communication.rsp.DeviceSupportFunctionRsp;
import com.oudmon.ble.base.communication.rsp.DisplayTimeRsp;
import com.oudmon.ble.base.communication.rsp.DndRsp;
import com.oudmon.ble.base.communication.rsp.FindPhoneRsp;
import com.oudmon.ble.base.communication.rsp.GlassModelControlResp;
import com.oudmon.ble.base.communication.rsp.HRVRsp;
import com.oudmon.ble.base.communication.rsp.HRVSettingRsp;
import com.oudmon.ble.base.communication.rsp.HeartRateSettingRsp;
import com.oudmon.ble.base.communication.rsp.MusicCommandRsp;
import com.oudmon.ble.base.communication.rsp.MuslimRsp;
import com.oudmon.ble.base.communication.rsp.MuslimTargetRsp;
import com.oudmon.ble.base.communication.rsp.PackageLengthRsp;
import com.oudmon.ble.base.communication.rsp.PalmScreenRsp;
import com.oudmon.ble.base.communication.rsp.PhoneNotifyRsp;
import com.oudmon.ble.base.communication.rsp.PressureRsp;
import com.oudmon.ble.base.communication.rsp.PressureSettingRsp;
import com.oudmon.ble.base.communication.rsp.ReadAlarmRsp;
import com.oudmon.ble.base.communication.rsp.ReadBlePressureRsp;
import com.oudmon.ble.base.communication.rsp.ReadDetailSportDataRsp;
import com.oudmon.ble.base.communication.rsp.ReadHeartRateRsp;
import com.oudmon.ble.base.communication.rsp.ReadMessagePushRsp;
import com.oudmon.ble.base.communication.rsp.ReadSitLongRsp;
import com.oudmon.ble.base.communication.rsp.ReadSleepDetailsRsp;
import com.oudmon.ble.base.communication.rsp.RealTimeHeartRateRsp;
import com.oudmon.ble.base.communication.rsp.SetTimeRsp;
import com.oudmon.ble.base.communication.rsp.SimpleStatusRsp;
import com.oudmon.ble.base.communication.rsp.StartHeartRateRsp;
import com.oudmon.ble.base.communication.rsp.TargetSettingRsp;
import com.oudmon.ble.base.communication.rsp.TimeFormatRsp;
import com.oudmon.ble.base.communication.rsp.TodaySportDataRsp;
import com.oudmon.ble.base.communication.rsp.TouchControlResp;
import com.oudmon.ble.base.communication.rsp.WeatherForecastRsp;

public class BeanFactory {
    public static BaseRspCmd createBean(int key, int type) {
        BaseRspCmd cmd = null;
        switch (key) {
            case 1: {
                cmd = new SetTimeRsp();
                break;
            }
            case 2: {
                cmd = new CameraNotifyRsp();
                break;
            }
            case 3: {
                cmd = new BatteryRsp();
                break;
            }
            case 4: 
            case 7: 
            case 8: 
            case 9: 
            case 14: {
                break;
            }
            case 5: {
                cmd = new PalmScreenRsp();
                break;
            }
            case 6: {
                cmd = new DndRsp();
                break;
            }
            case 10: {
                cmd = new TimeFormatRsp();
                break;
            }
            case 12: {
                cmd = new BpSettingRsp();
                break;
            }
            case 13: {
                cmd = new BpDataRsp();
                break;
            }
            case 25: {
                cmd = new DegreeSwitchRsp();
                break;
            }
            case 31: {
                cmd = new DisplayTimeRsp();
                break;
            }
            case 33: {
                cmd = new TargetSettingRsp();
                break;
            }
            case 34: {
                cmd = new FindPhoneRsp();
                break;
            }
            case 50: {
                cmd = new DeviceAvatarRsp();
                break;
            }
            case 60: {
                cmd = new DeviceSupportFunctionRsp();
                break;
            }
            case 54: {
                cmd = new PressureSettingRsp();
                break;
            }
            case 55: {
                cmd = new PressureRsp();
                break;
            }
            case 56: {
                cmd = new HRVSettingRsp();
                break;
            }
            case 57: {
                cmd = new HRVRsp();
                break;
            }
            case 22: {
                cmd = new HeartRateSettingRsp();
                break;
            }
            case 29: {
                cmd = new MusicCommandRsp();
                break;
            }
            case 47: {
                cmd = new PackageLengthRsp();
                break;
            }
            case 17: {
                cmd = new PhoneNotifyRsp();
                break;
            }
            case 40: {
                cmd = new ReadAlarmRsp();
                break;
            }
            case 20: {
                cmd = new ReadBlePressureRsp();
                break;
            }
            case 67: {
                cmd = new ReadDetailSportDataRsp();
                break;
            }
            case 21: {
                cmd = new ReadHeartRateRsp();
                break;
            }
            case 38: {
                cmd = new ReadSitLongRsp();
                break;
            }
            case 68: {
                cmd = new ReadSleepDetailsRsp();
                break;
            }
            case 30: {
                cmd = new RealTimeHeartRateRsp();
                break;
            }
            case 114: {
                cmd = new SimpleStatusRsp();
                break;
            }
            case 97: {
                cmd = new ReadMessagePushRsp();
                break;
            }
            case 105: {
                cmd = new StartHeartRateRsp();
                break;
            }
            case 72: {
                cmd = new TodaySportDataRsp();
                break;
            }
            case 26: {
                cmd = new WeatherForecastRsp();
                break;
            }
            case 44: {
                cmd = new BloodOxygenSettingRsp();
                break;
            }
            case 115: 
            case 120: {
                cmd = new DeviceNotifyRsp();
                break;
            }
            case 122: {
                cmd = new MuslimRsp();
                break;
            }
            case 119: {
                cmd = new AppSportRsp();
                break;
            }
            case 59: {
                cmd = new TouchControlResp();
                break;
            }
            case 62: {
                cmd = new GlassModelControlResp();
                break;
            }
            case -223: {
                cmd = new AppRevisionResp();
                break;
            }
            case 58: {
                cmd = new BloodSugarLipidsSettingRsp();
                break;
            }
            case 123: {
                cmd = new MuslimTargetRsp();
            }
        }
        return cmd;
    }
}

