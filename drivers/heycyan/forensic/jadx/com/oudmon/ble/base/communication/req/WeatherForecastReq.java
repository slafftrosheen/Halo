/*
 * Decompiled with CFR 0.152.
 */
package com.oudmon.ble.base.communication.req;

import com.oudmon.ble.base.communication.req.MixtureReq;
import com.oudmon.ble.base.communication.utils.DataParseUtils;

public class WeatherForecastReq
extends MixtureReq {
    private WeatherForecastReq() {
        super((byte)26);
    }

    public static WeatherForecastReq getWriteInstance(final WeatherForecastBuilder builder) {
        return new WeatherForecastReq(){
            {
                this.subData = new byte[10];
                this.subData[0] = (byte)builder.index;
                System.arraycopy(DataParseUtils.intToByteArray((int)builder.timeStamp), 0, this.subData, 1, 4);
                this.subData[5] = (byte)builder.weatherType;
                this.subData[6] = (byte)builder.minDegree;
                this.subData[7] = (byte)builder.maxDegree;
                this.subData[8] = (byte)builder.humidity;
                this.subData[9] = (byte)(builder.takeUmbrella ? 1 : 2);
            }
        };
    }

    public static class WeatherForecastBuilder {
        private int index;
        private long timeStamp;
        private int weatherType;
        private int minDegree;
        private int maxDegree;
        private int humidity;
        private boolean takeUmbrella;

        public WeatherForecastBuilder setIndex(int index) {
            this.index = index;
            return this;
        }

        public WeatherForecastBuilder setTimeStamp(long timeStamp) {
            this.timeStamp = timeStamp;
            return this;
        }

        public WeatherForecastBuilder setWeatherType(int weatherType) {
            this.weatherType = weatherType;
            return this;
        }

        public WeatherForecastBuilder setMinDegree(int minDegree) {
            this.minDegree = minDegree;
            return this;
        }

        public WeatherForecastBuilder setMaxDegree(int maxDegree) {
            this.maxDegree = maxDegree;
            return this;
        }

        public WeatherForecastBuilder setHumidity(int humidity) {
            this.humidity = humidity;
            return this;
        }

        public WeatherForecastBuilder setTakeUmbrella(boolean takeUmbrella) {
            this.takeUmbrella = takeUmbrella;
            return this;
        }

        public String toString() {
            return "WeatherForecastBuilder{index=" + this.index + ", timeStamp=" + this.timeStamp + ", weatherType=" + this.weatherType + ", minDegree=" + this.minDegree + ", maxDegree=" + this.maxDegree + ", humidity=" + this.humidity + ", takeUmbrella=" + this.takeUmbrella + '}';
        }
    }
}

