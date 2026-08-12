/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.bigData;

import java.util.List;

public class CustomWatchFaceEntity {
    List<CustomElement> data;

    public List<CustomElement> getData() {
        return this.data;
    }

    public void setData(List<CustomElement> data) {
        this.data = data;
    }

    public static class CustomElement {
        int type;
        int x;
        int y;
        int r;
        int g;
        int b;

        public int getType() {
            return this.type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public int getX() {
            return this.x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return this.y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getR() {
            return this.r;
        }

        public void setR(int r) {
            this.r = r;
        }

        public int getG() {
            return this.g;
        }

        public void setG(int g) {
            this.g = g;
        }

        public int getB() {
            return this.b;
        }

        public void setB(int b) {
            this.b = b;
        }
    }
}

