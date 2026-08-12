/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;
import com.oudmon.ble.base.communication.utils.ByteUtil;

public class PhoneGpsReq
extends MixtureReq {
    private PhoneGpsReq() {
        super((byte)116);
    }

    public static PhoneGpsReq getGpsStatus(final byte status) {
        return new PhoneGpsReq(){
            {
                this.subData = new byte[]{status, 0};
            }
        };
    }

    public static PhoneGpsReq setPhoneDataReq(final int distance, final int calorie) {
        return new PhoneGpsReq(){
            {
                byte[] distanceArray = ByteUtil.intToByte(distance, 4);
                byte[] calorieArray = ByteUtil.intToByte(calorie, 4);
                byte[] tempA = ByteUtil.concat(new byte[]{5, 0}, distanceArray);
                this.subData = ByteUtil.concat(tempA, calorieArray);
            }
        };
    }
}

