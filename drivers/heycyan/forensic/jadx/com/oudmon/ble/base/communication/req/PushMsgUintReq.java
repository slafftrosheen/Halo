/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.BaseReqCmd;

public class PushMsgUintReq
extends BaseReqCmd {
    public static final byte TYPE_PHONE_RING = 0;
    public static final byte TYPE_SMS = 1;
    public static final byte TYPE_QQ = 2;
    public static final byte TYPE_WECHAT = 3;
    public static final byte TYPE_PHONE_ACTION = 4;
    public static final byte TYPE_FACEBOOK = 5;
    public static final byte TYPE_WHATSAPP = 6;
    public static final byte TYPE_TWITTER = 7;
    public static final byte TYPE_SKYPE = 8;
    public static final byte TYPE_Line = 9;
    private byte[] subData;

    public PushMsgUintReq(byte type, int total, int index, byte[] msgData) {
        super((byte)114);
        this.subData = new byte[msgData.length + 3];
        this.subData[0] = type;
        this.subData[1] = (byte)total;
        this.subData[2] = (byte)index;
        System.arraycopy(msgData, 0, this.subData, 3, msgData.length);
    }

    @Override
    protected byte[] getSubData() {
        return this.subData;
    }
}

