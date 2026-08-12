/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import com.oudmon.ble.base.communication.rsp.BaseRspCmd;
import com.oudmon.ble.base.communication.utils.ByteUtil;
import java.util.Arrays;

public class SetTimeRsp
extends BaseRspCmd {
    public boolean mSupportTemperature;
    public boolean mSupportPlate;
    public boolean mSupportMenstruation;
    public boolean mSupportCustomWallpaper;
    public boolean mSupportBloodOxygen;
    public boolean mSupportBloodPressure;
    public boolean mSupportFeature;
    public boolean mSupportOneKeyCheck;
    public boolean mSupportWeather;
    public boolean mNewSleepProtocol;
    public int mMaxWatchFace;
    public boolean mSupportContact;
    public boolean mSupportManualHeart;
    public boolean mSupportECard;
    public boolean mSupportLocation;
    public int mMaxContacts;
    public boolean mMusicSupport;
    public boolean rtkMcu;
    public boolean mEbookSupport;
    public boolean mSupportWeChat;
    public boolean mSupportAvatar;
    public boolean mSupportBloodSugar;
    public int width;
    public int height;
    public boolean mSupportLyrics;
    public boolean mSupportAlbum;
    public boolean mSupportGPS;
    public boolean mSupportJieLiMusic;
    public boolean mSupport4G;
    public boolean bpSettingSupport;
    public boolean mSupportNavPicture;
    public boolean mSupportPressure;
    public boolean mSupportHrv;
    public boolean mSupportManualBloodOxygen;
    public boolean mSupportAppMeasure;
    public boolean mYaWeiSupport;
    public boolean mSupportRecord;

    @Override
    public boolean acceptData(byte[] data) {
        this.mSupportTemperature = data[0] == 1;
        this.mSupportPlate = data[1] == 1;
        this.mSupportMenstruation = data[2] == 1;
        this.mSupportCustomWallpaper = (data[3] & 1) != 0;
        this.mSupportBloodOxygen = (data[3] & 2) != 0;
        this.mSupportBloodPressure = (data[3] & 4) != 0;
        this.mSupportFeature = (data[3] & 8) != 0;
        this.mSupportOneKeyCheck = (data[3] & 0x10) != 0;
        this.mSupportWeather = (data[3] & 0x20) != 0;
        this.mSupportWeChat = (data[3] & 0x40) == 0;
        this.mSupportAvatar = (data[3] & 0x80) != 0;
        this.width = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 4, 6));
        this.height = ByteUtil.bytesToInt(Arrays.copyOfRange(data, 6, 8));
        this.mSupportAppMeasure = (data[10] & 0x20) != 0;
        this.mSupportManualBloodOxygen = (data[10] & 0x40) != 0;
        this.mYaWeiSupport = (data[10] & 0x80) != 0;
        this.mNewSleepProtocol = data[8] == 1;
        this.mMaxWatchFace = data[9];
        this.mSupportContact = (data[10] & 1) != 0;
        this.mSupportLyrics = (data[10] & 2) != 0;
        this.mSupportAlbum = (data[10] & 4) != 0;
        this.mSupportGPS = (data[10] & 8) != 0;
        this.mSupportJieLiMusic = (data[10] & 0x10) != 0;
        this.mSupportManualHeart = (data[11] & 1) != 0;
        this.mSupportECard = (data[11] & 2) != 0;
        this.mSupportLocation = (data[11] & 4) != 0;
        this.mMusicSupport = (data[11] & 0x10) != 0;
        this.rtkMcu = (data[11] & 0x20) != 0;
        this.mEbookSupport = (data[11] & 0x40) != 0;
        this.mSupportBloodSugar = (data[11] & 0x80) != 0;
        this.mMaxContacts = data[12] == 0 ? 20 : data[12] * 10;
        this.bpSettingSupport = (data[13] & 2) != 0;
        this.mSupport4G = (data[13] & 4) != 0;
        this.mSupportNavPicture = (data[13] & 8) != 0;
        this.mSupportPressure = (data[13] & 0x10) != 0;
        this.mSupportHrv = (data[13] & 0x20) != 0;
        this.mSupportRecord = (data[13] & 1) != 0;
        return false;
    }
}

