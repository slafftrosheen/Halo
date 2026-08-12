/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.rsp;

import java.util.List;

public class SleepNewProtoResp {
    private int st;
    private int et;
    private List<DetailBean> list;

    public int getSt() {
        return this.st;
    }

    public void setSt(int st) {
        this.st = st;
    }

    public int getEt() {
        return this.et;
    }

    public void setEt(int et) {
        this.et = et;
    }

    public List<DetailBean> getList() {
        return this.list;
    }

    public void setList(List<DetailBean> list) {
        this.list = list;
    }

    public static class DetailBean {
        private int d;
        private int t;

        public int getD() {
            return this.d;
        }

        public void setD(int d) {
            this.d = d;
        }

        public int getT() {
            return this.t;
        }

        public void setT(int t) {
            this.t = t;
        }
    }
}

