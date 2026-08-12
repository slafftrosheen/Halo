/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.file;

public class PlateEntity {
    public boolean mDelete = false;
    public String mPlateName = "";

    public PlateEntity(boolean mDelete, String mPlateName) {
        this.mDelete = mDelete;
        this.mPlateName = mPlateName;
    }

    public String toString() {
        return "PlateEntity{mDelete=" + this.mDelete + ", mPlateName='" + this.mPlateName + '\'' + '}';
    }
}

