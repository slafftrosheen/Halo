/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData.resp;

import com.oudmon.ble.base.communication.bigData.resp.AiChatResponse;
import com.oudmon.ble.base.communication.bigData.resp.BaseResponse;
import com.oudmon.ble.base.communication.bigData.resp.BatteryResponse;
import com.oudmon.ble.base.communication.bigData.resp.ClassBluetoothResponse;
import com.oudmon.ble.base.communication.bigData.resp.DeviceInfoResponse;
import com.oudmon.ble.base.communication.bigData.resp.GlassModelControlResponse;
import com.oudmon.ble.base.communication.bigData.resp.GlassesAiVoicePlayStatusRsp;
import com.oudmon.ble.base.communication.bigData.resp.GlassesAiVoiceRsp;
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyRsp;
import com.oudmon.ble.base.communication.bigData.resp.GlassesTouchSupportRsp;
import com.oudmon.ble.base.communication.bigData.resp.GlassesWearRsp;
import com.oudmon.ble.base.communication.bigData.resp.PictureThumbnailsResponse;
import com.oudmon.ble.base.communication.bigData.resp.SyncTimeResponse;
import com.oudmon.ble.base.communication.bigData.resp.VolumeControlResponse;

public class BigDataBeanFactory {
    public static BaseResponse createBean(int key) {
        BaseResponse cmd = null;
        switch (key) {
            case 65: {
                cmd = new GlassModelControlResponse();
                break;
            }
            case 46: {
                cmd = new ClassBluetoothResponse();
                break;
            }
            case 64: {
                cmd = new SyncTimeResponse();
                break;
            }
            case -3: {
                cmd = new PictureThumbnailsResponse();
                break;
            }
            case 115: {
                cmd = new GlassesDeviceNotifyRsp();
                break;
            }
            case 89: {
                cmd = new AiChatResponse();
                break;
            }
            case 66: {
                cmd = new BatteryResponse();
                break;
            }
            case 67: {
                cmd = new DeviceInfoResponse();
                break;
            }
            case 68: {
                cmd = new GlassesAiVoiceRsp();
                break;
            }
            case 70: {
                cmd = new GlassesWearRsp();
                break;
            }
            case 72: {
                cmd = new GlassesAiVoicePlayStatusRsp();
                break;
            }
            case 71: {
                cmd = new GlassesTouchSupportRsp();
                break;
            }
            case 81: {
                cmd = new VolumeControlResponse();
                break;
            }
        }
        return cmd;
    }
}

