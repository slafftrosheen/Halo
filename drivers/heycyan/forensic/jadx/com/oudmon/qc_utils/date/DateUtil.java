/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  android.os.Build$VERSION
 */
package com.oudmon.qc_utils.date;

import android.os.Build;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

public class DateUtil {
    public static long Hour_S_Min = 3600L;
    public static final Locale localeObject = new Locale("en");
    private static final ThreadLocal<SimpleDateFormat> dFMMdd = new ThreadLocal<SimpleDateFormat>(){

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("MM/dd", localeObject);
        }
    };
    private static final ThreadLocal<SimpleDateFormat> dFMMdd_HHmm = new ThreadLocal<SimpleDateFormat>(){

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("MM-dd HH:mm", localeObject);
        }
    };
    private static final ThreadLocal<SimpleDateFormat> dFyyyyMM = new ThreadLocal<SimpleDateFormat>(){

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM", localeObject);
        }
    };
    private static final ThreadLocal<SimpleDateFormat> dFyyyyMMdd = new ThreadLocal<SimpleDateFormat>(){

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(DateUtil.dFyyyyMMdd1, localeObject);
        }
    };
    private static final ThreadLocal<SimpleDateFormat> dfddMMyyy = new ThreadLocal<SimpleDateFormat>(){

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("dd-MM-yyyy", localeObject);
        }
    };
    private static final ThreadLocal<SimpleDateFormat> dFyyyyMMdd_HHmm = new ThreadLocal<SimpleDateFormat>(){

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(DateUtil.yyyyMMdd_HHmm, localeObject);
        }
    };
    private static final ThreadLocal<SimpleDateFormat> dFyyyyMMdd_HHmmss = new ThreadLocal<SimpleDateFormat>(){

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat(DateUtil.yyyyMMdd_HHmmss, localeObject);
        }
    };
    private static final ThreadLocal<SimpleDateFormat> dFHHmm = new ThreadLocal<SimpleDateFormat>(){

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("HH:mm", localeObject);
        }
    };
    private static final ThreadLocal<SimpleDateFormat> dFHHmmss = new ThreadLocal<SimpleDateFormat>(){

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("HH:mm:ss", localeObject);
        }
    };
    private static final ThreadLocal<SimpleDateFormat> dFSyyyyMMdd = new ThreadLocal<SimpleDateFormat>(){

        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyyMMdd", localeObject);
        }
    };
    private Calendar c = Calendar.getInstance();
    public static final String yyyyMMdd_HHmmss = "yyyy-MM-dd HH:mm:ss";
    public static final String yyyyMMdd_HHmm = "yyyy-MM-dd HH:mm";
    public static final String dFyyyyMMdd1 = "yyyy-MM-dd";
    public static SimpleDateFormat yyyyMMdd_HHmmssF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static SimpleDateFormat dFyyyyMMddF = new SimpleDateFormat("yyyy-MM-dd");
    public static SimpleDateFormat dFyyyyMMddmmF = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public long getZeroTime() {
        DateUtil dateUtil = new DateUtil(this.getYear(), this.getMonth(), this.getDay());
        return dateUtil.getUnixTimestamp();
    }

    public String getZeroTimeYyyyMMdd_HHmmssDate() {
        DateUtil dateUtil = new DateUtil(this.getYear(), this.getMonth(), this.getDay());
        return dateUtil.getYyyyMMdd_HHmmssDate();
    }

    public long getZeroTime1() {
        DateUtil dateUtil = new DateUtil(this.getYear(), this.getMonth(), this.getDay());
        return dateUtil.getTimestamp();
    }

    public static long getFirstDayMonth(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(2, 0);
        c.set(5, 1);
        return c.getTimeInMillis();
    }

    public static DateUtil getFirstDayOfMonth(DateUtil dateUtil) {
        return new DateUtil(DateUtil.getFirstDayMonth(dateUtil.toDate()), false);
    }

    public static DateUtil getLastDayOfMonth(DateUtil dateUtil) {
        return new DateUtil(DateUtil.getLastDayMonth(dateUtil.toDate()), false);
    }

    public static long getLastDayMonth(Date date) {
        Calendar ca = Calendar.getInstance();
        ca.setTime(date);
        ca.add(2, 0);
        ca.set(5, ca.getActualMaximum(5));
        return ca.getTimeInMillis();
    }

    public static int getDaysOfMonth(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        return calendar.getActualMaximum(5);
    }

    public int getTodayMin() {
        long zeroTime = this.getZeroTime1();
        int i = Math.round((this.c.getTimeInMillis() - zeroTime) / 60000L) + 1;
        return i;
    }

    public int getTodayMinNoPlus() {
        long zeroTime = this.getZeroTime1();
        int i = Math.round((this.c.getTimeInMillis() - zeroTime) / 60000L);
        return i;
    }

    public static boolean isSameDay(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        boolean isSameYear = cal1.get(1) == cal2.get(1);
        boolean isSameMonth = isSameYear && cal1.get(2) == cal2.get(2);
        boolean isSameDate = isSameMonth && cal1.get(5) == cal2.get(5);
        return isSameDate;
    }

    public boolean isSameDay(long compare_time, boolean isUnix) {
        DateUtil compare_dt = new DateUtil(compare_time, isUnix);
        return compare_dt.getYear() == this.getYear() && compare_dt.getMonth() == this.getMonth() && compare_dt.getDay() == this.getDay();
    }

    public static boolean isSameMonth(Date date1, Date date2) {
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(date1);
        Calendar cal2 = Calendar.getInstance();
        cal2.setTime(date2);
        boolean isSameYear = cal1.get(1) == cal2.get(1);
        boolean isSameMonth = isSameYear && cal1.get(2) == cal2.get(2);
        return isSameMonth;
    }

    public static long getPreOrNextTimeByDay(long marignSize) {
        long lastTime = System.currentTimeMillis();
        return lastTime - marignSize * 86400000L;
    }

    public static long getGMTDate(long record_date) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm");
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            String time = sdf.format(new Date(record_date * 1000L));
            String ti_year = time.substring(0, 4);
            String ti_month = time.substring(5, 7);
            String ti_date = time.substring(8, 10);
            String ti_hour = time.substring(11, 13);
            String ti_min = time.substring(14, 16);
            return new DateUtil(Integer.parseInt(ti_year), Integer.parseInt(ti_month), Integer.parseInt(ti_date), Integer.parseInt(ti_hour), Integer.parseInt(ti_min), 0).getUnixTimestamp();
        }
        catch (NumberFormatException e) {
            e.printStackTrace();
            DateUtil dateUtil = new DateUtil(record_date, true);
            dateUtil.setHour(0);
            dateUtil.setMinute(0);
            dateUtil.setSecond(0);
            return dateUtil.getUnixTimestamp();
        }
    }

    public static String dayMinToStrShow(int mins) {
        String strH = "";
        String strM = "";
        int h = mins / 60;
        int min = mins % 60;
        if (h > 0) {
            strH = h < 10 ? "0" + h : h + "";
            strM = min < 10 ? "0" + min : min + "";
            return strH + "h" + strM + "min";
        }
        strM = min < 10 ? "0" + min : min + "";
        return strM + "min";
    }

    public static DateUtil valueOf(String sdate) {
        String MMddFmt = "[0-9]{2}-[0-9]{2}";
        String MMdd_HHmmFmt = "[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}";
        String yyyyMMFmt = "[0-9]{4}-[0-9]{2}";
        String yyyyMMddFmt = "[0-9]{4}-[0-9]{2}-[0-9]{2}";
        String yyyyMMdd_HHmmFmt = "[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}";
        String yyyyMMdd_HHmmssFmt = "[0-9]{4}-[0-9]{2}-[0-9]{2} [0-9]{2}:[0-9]{2}:[0-9]{2}";
        String HHmmFmt = "[0-9]{2}:[0-9]{2}";
        String HHmmssFmt = "[0-9]{2}:[0-9]{2}:[0-9]{2}";
        Pattern p = Pattern.compile(yyyyMMdd_HHmmssFmt);
        try {
            p = Pattern.compile(MMddFmt);
            if (p.matcher(sdate).matches()) {
                return DateUtil.parse(sdate, DateFormater.MMdd);
            }
            p = Pattern.compile(MMdd_HHmmFmt);
            if (p.matcher(sdate).matches()) {
                return DateUtil.parse(sdate, DateFormater.MMdd_HHmm);
            }
            p = Pattern.compile(yyyyMMFmt);
            if (p.matcher(sdate).matches()) {
                return DateUtil.parse(sdate, DateFormater.yyyyMM);
            }
            p = Pattern.compile(yyyyMMddFmt);
            if (p.matcher(sdate).matches()) {
                return DateUtil.parse(sdate, DateFormater.yyyyMMdd);
            }
            p = Pattern.compile(yyyyMMdd_HHmmFmt);
            if (p.matcher(sdate).matches()) {
                return DateUtil.parse(sdate, DateFormater.yyyyMMdd_HHmm);
            }
            p = Pattern.compile(yyyyMMdd_HHmmssFmt);
            if (p.matcher(sdate).matches()) {
                return DateUtil.parse(sdate, DateFormater.yyyyMMdd_HHmmss);
            }
            p = Pattern.compile(HHmmFmt);
            if (p.matcher(sdate).matches()) {
                return DateUtil.parse(sdate, DateFormater.HHmm);
            }
            p = Pattern.compile(HHmmssFmt);
            if (p.matcher(sdate).matches()) {
                return DateUtil.parse(sdate, DateFormater.HHmmss);
            }
        }
        catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static DateUtil parse(String sdate, DateFormater formater) throws ParseException {
        Date date = null;
        switch (formater) {
            case MMdd: {
                date = dFMMdd.get().parse(sdate);
                break;
            }
            case MMdd_HHmm: {
                date = dFMMdd_HHmm.get().parse(sdate);
                break;
            }
            case yyyyMM: {
                date = dFyyyyMM.get().parse(sdate);
                break;
            }
            case yyyyMMdd: {
                date = dFSyyyyMMdd.get().parse(sdate);
                break;
            }
            case dFyyyyMMdd: {
                date = dFyyyyMMdd.get().parse(sdate);
                break;
            }
            case dfddMMyyy: {
                date = dfddMMyyy.get().parse(sdate);
                break;
            }
            case yyyyMMdd_HHmm: {
                date = dFyyyyMMdd_HHmm.get().parse(sdate);
                break;
            }
            case yyyyMMdd_HHmmss: {
                date = dFyyyyMMdd_HHmmss.get().parse(sdate);
                break;
            }
            case HHmm: {
                date = dFHHmm.get().parse(sdate);
                break;
            }
            case HHmmss: {
                date = dFHHmmss.get().parse(sdate);
            }
        }
        return new DateUtil(date);
    }

    public DateUtil() {
    }

    public DateUtil(TimeZone timeZone) {
        this.c.setTimeZone(timeZone);
    }

    public DateUtil(long timestamp, boolean isUnix) {
        if (isUnix) {
            this.c.setTimeInMillis(timestamp * 1000L);
        } else {
            this.c.setTimeInMillis(timestamp);
        }
    }

    public DateUtil(long timestamp, boolean isUnix, TimeZone timeZone) {
        this.c.setTimeZone(timeZone);
        if (isUnix) {
            this.c.setTimeInMillis(timestamp * 1000L);
        } else {
            this.c.setTimeInMillis(timestamp);
        }
    }

    public DateUtil(Date date) {
        this.c.setTime(date);
    }

    public DateUtil(int year, int month, int day) {
        this(year, month, day, 0, 0, 0);
    }

    public boolean futureDate() {
        DateUtil d = new DateUtil();
        return this.getUnixTimestamp() > d.getUnixTimestamp();
    }

    public DateUtil(int year, int month, int day, int hour, int minute) {
        this(year, month, day, hour, minute, 0);
    }

    public DateUtil(int year, int month, int day, int hour, int minute, int second) {
        this.c.set(1, year);
        this.c.set(2, month - 1);
        this.c.set(5, day);
        this.c.set(11, hour);
        this.c.set(12, minute);
        this.c.set(13, second);
    }

    public DateUtil(int hour, int minute) {
        this.c.set(11, hour);
        this.c.set(12, minute);
    }

    public boolean isToday() {
        DateUtil d = new DateUtil();
        return this.getYear() == d.getYear() && this.getMonth() == d.getMonth() && this.getDay() == d.getDay();
    }

    public boolean isYesterday() {
        DateUtil d = new DateUtil();
        d.addDay(-1);
        return this.getYear() == d.getYear() && this.getMonth() == d.getMonth() && this.getDay() == d.getDay();
    }

    public boolean isSameWeek(int number) {
        DateUtil date = new DateUtil(new Date());
        int index = date.getWeekOfYear();
        return number == index;
    }

    public boolean isSameMonth(int month, int year) {
        int index = this.getMonth();
        int nYear = this.getYear();
        return month == index && nYear == year;
    }

    public int daysBetweenMe(DateUtil dateUtil) {
        return (int)(Math.abs(this.getZeroTime() - dateUtil.getZeroTime()) / 86400L);
    }

    public static String dayMinToStr(int mins) {
        String strH = "";
        String strM = "";
        int h = mins / 60;
        int min = mins % 60;
        strH = h < 10 ? "0" + h : h + "";
        strM = min < 10 ? "0" + min : min + "";
        return strH + ":" + strM;
    }

    public static String dayMinToStrChina(int mins) {
        String strH = "";
        String strM = "";
        int h = mins / 60;
        int min = mins % 60;
        if (h == 0) {
            strM = min < 10 ? "0" + min : min + "";
            return strM + "\u5206";
        }
        strH = h < 10 ? "0" + h : h + "";
        strM = min < 10 ? "0" + min : min + "";
        return strH + "\u65f6" + strM + "\u5206";
    }

    public static String formatMillis(long millis) {
        long m = millis / 60000L;
        long s = millis % 60000L / 1000L;
        return (m < 10L ? "0" : "") + m + (s < 10L ? ":0" : ":") + s;
    }

    public static String formatMillisToMinutesSecondsTenths(long millis) {
        long m = millis / 60000L;
        long s = millis % 60000L / 1000L;
        long t = millis % 1000L / 100L;
        return (m < 10L ? "0" : "") + m + (s < 10L ? ":0" : ":") + s;
    }

    public static String secondToStr(int seconds) {
        String strH = "";
        String strM = "";
        int h = seconds / 3600;
        int m = seconds % 3600 / 60;
        int s = seconds % 60;
        strH = h < 10 ? "0" + h : h + "";
        strM = m < 10 ? "0" + m : m + "";
        String strS = s < 10 ? "0" + s : s + "";
        if (h == 0) {
            return strM + ":" + strS;
        }
        return strH + ":" + strM + ":" + strS;
    }

    public static String dayMinToStrSymbol(int mins) {
        String strH = "";
        String strM = "";
        int h = mins / 60;
        int min = mins % 60;
        strH = h < 10 ? "0" + h : h + "";
        strM = min < 10 ? "0" + min : min + "";
        return strH + "'" + strM + "''";
    }

    public static String minsToHHmmdd(int seconds) {
        int temp = 0;
        StringBuffer sb = new StringBuffer();
        temp = seconds / 3600;
        sb.append(temp < 10 ? "0" + temp + ":" : "" + temp + ":");
        temp = seconds % 3600 / 60;
        sb.append(temp < 10 ? "0" + temp + ":" : "" + temp + ":");
        temp = seconds % 3600 % 60;
        sb.append(temp < 10 ? "0" + temp : "" + temp);
        return sb.toString();
    }

    public Date toDate() {
        return this.c.getTime();
    }

    public String toFormatString(DateFormater formater) {
        Date date = this.toDate();
        String sdate = "Unknown";
        switch (formater) {
            case MMdd: {
                sdate = dFMMdd.get().format(date);
                break;
            }
            case MMdd_HHmm: {
                sdate = dFMMdd_HHmm.get().format(date);
                break;
            }
            case yyyyMM: {
                sdate = dFyyyyMM.get().format(date);
                break;
            }
            case yyyyMMdd: 
            case dFyyyyMMdd: {
                sdate = dFyyyyMMdd.get().format(date);
                break;
            }
            case yyyyMMdd_HHmm: {
                sdate = dFyyyyMMdd_HHmm.get().format(date);
                break;
            }
            case yyyyMMdd_HHmmss: {
                sdate = dFyyyyMMdd_HHmmss.get().format(date);
                break;
            }
            case HHmm: {
                sdate = dFHHmm.get().format(date);
                break;
            }
            case HHmmss: {
                sdate = dFHHmmss.get().format(date);
                break;
            }
            case SyyyyMMdd: {
                sdate = dFSyyyyMMdd.get().format(date);
                break;
            }
            case dfddMMyyy: {
                sdate = dfddMMyyy.get().format(date);
            }
        }
        return sdate;
    }

    public static Date String2Date(String formater, String dateString) {
        Date date = null;
        try {
            switch (formater) {
                case "yyyy-MM-dd HH:mm:ss": {
                    date = yyyyMMdd_HHmmssF.parse(dateString);
                    break;
                }
                case "yyyy-MM-dd HH:mm": {
                    date = dFyyyyMMddmmF.parse(dateString);
                    break;
                }
                case "yyyy-MM-dd": {
                    date = dFyyyyMMddF.parse(dateString);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return date;
    }

    public String getMMddDate() {
        return this.toFormatString(DateFormater.MMdd);
    }

    public String getMMdd_HHmmDate() {
        return this.toFormatString(DateFormater.MMdd_HHmm);
    }

    public String getY_M_D() {
        return this.toFormatString(DateFormater.dFyyyyMMdd);
    }

    public String getD_M_Y() {
        return this.toFormatString(DateFormater.dfddMMyyy);
    }

    public String getY_M_D_H_M_S() {
        return this.toFormatString(DateFormater.yyyyMMdd_HHmmss);
    }

    public String getY_M_D_H_M() {
        return this.toFormatString(DateFormater.yyyyMMdd_HHmm);
    }

    public String getYyyyMMDate() {
        return this.toFormatString(DateFormater.yyyyMM);
    }

    public String getYyyyMMddDate() {
        return this.toFormatString(DateFormater.yyyyMMdd);
    }

    public String getYyyyMMdd_HHmmDate() {
        return this.toFormatString(DateFormater.yyyyMMdd_HHmm);
    }

    public String getYyyyMMdd_HHmmssDate() {
        return this.toFormatString(DateFormater.yyyyMMdd_HHmmss);
    }

    public String getHHmmDate() {
        return this.toFormatString(DateFormater.HHmm);
    }

    public String getHHmmssDate() {
        return this.toFormatString(DateFormater.HHmmss);
    }

    public String getSyyyyMMddDate() {
        return this.toFormatString(DateFormater.SyyyyMMdd);
    }

    public String getyyyyMMddDate() {
        return this.toFormatString(DateFormater.yyyyMMdd);
    }

    public int getYear() {
        return this.c.get(1);
    }

    public void setYear(int year) {
        this.c.set(1, year);
    }

    public int getMonth() {
        return this.c.get(2) + 1;
    }

    public void setMonth(int month) {
        this.c.set(2, month - 1);
    }

    public int getDay() {
        return this.c.get(5);
    }

    public int getDaysOfThisMonth() {
        return this.c.get(5);
    }

    public void setDay(int day) {
        this.c.set(5, day);
    }

    public void addDay(int day) {
        this.c.add(5, day);
    }

    public void addMonth(int month) {
        this.c.add(2, month);
    }

    public int getHour() {
        return this.c.get(11);
    }

    public void setHour(int hour) {
        this.c.set(11, hour);
    }

    public int getMinute() {
        return this.c.get(12);
    }

    public void setMinute(int minute) {
        this.c.set(12, minute);
    }

    public int getSecond() {
        return this.c.get(13);
    }

    public void setSecond(int second) {
        this.c.set(13, second);
    }

    public long getTimestamp() {
        return this.c.getTimeInMillis();
    }

    public void setTimestamp(long timestamp) {
        this.c.setTimeInMillis(timestamp);
    }

    public long getUnixTimestamp() {
        return this.c.getTimeInMillis() / 1000L;
    }

    public void setUnixTimestamp(long unix_timestamp) {
        this.c.setTimeInMillis(unix_timestamp * 1000L);
    }

    public int getDayOfWeek() {
        return this.c.get(7);
    }

    public int getWeekOfYear() {
        return this.c.get(3);
    }

    public int getWeekOfMonth() {
        return this.c.get(4);
    }

    public String getMonDate() {
        int day = this.getDayOfWeek();
        this.c.add(5, this.c.getFirstDayOfWeek() - day);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        return sdf.format(this.c.getTime());
    }

    public String toString() {
        return this.getYyyyMMdd_HHmmssDate();
    }

    public static String getTime(long time) {
        long now = System.currentTimeMillis();
        long dTime = (now - time) / 86400000L;
        DateUtil dateUtil = new DateUtil(time, false);
        if (dTime > 0L) {
            return dateUtil.getYyyyMMddDate();
        }
        return dateUtil.getHHmmDate();
    }

    public static long getSunDayTimeFromWeek() {
        Calendar cal = Calendar.getInstance();
        int i = cal.get(7) - 1;
        long l = cal.getTime().getTime() - (long)(i * 86400000);
        return l;
    }

    public static DateUtil firstDayOfWeek() {
        Calendar cal = Calendar.getInstance();
        cal.setFirstDayOfWeek(1);
        cal.setMinimalDaysInFirstWeek(7);
        cal.setTimeInMillis(System.currentTimeMillis());
        cal.add(4, 0);
        cal.set(7, 1);
        Date time = cal.getTime();
        return new DateUtil(time);
    }

    public static DateUtil firstDayOfWeekRing(DateUtil dateUtil) {
        Date date = new Date(dateUtil.getTimestamp());
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        calendar.setFirstDayOfWeek(2);
        int dayOfWeek = calendar.get(7);
        if (dayOfWeek == 1) {
            dayOfWeek = 8;
        }
        int offset = dayOfWeek - 2;
        calendar.add(5, -offset);
        return new DateUtil(calendar.getTime());
    }

    public static LocalDate getWeekStartDateFromTimestamp(long timestamp) {
        LocalDate date = null;
        if (Build.VERSION.SDK_INT >= 26) {
            date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate();
        }
        if (Build.VERSION.SDK_INT >= 26) {
            return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        }
        return null;
    }

    public static int getDayOfMonth() {
        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        int day = cal.get(5);
        return day - 1;
    }

    public static Date getDateByWeekMagin(int size) {
        long sunDayTimeFromWeek = DateUtil.getSunDayTimeFromWeek();
        return new Date(sunDayTimeFromWeek + (long)(size * 86400000));
    }

    public static int differentDaysByMillisecond(Date date1, Date date2) {
        int days = (int)((date2.getTime() - date1.getTime()) / 86400000L);
        return days;
    }

    public static long dateStr2Stamp(String date) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
            return Long.parseLong(String.valueOf(sdf.parse(date).getTime()));
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public static long dateY_M_D2Stamp(String date) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(dFyyyyMMdd1);
            return Long.parseLong(String.valueOf(sdf.parse(date).getTime()));
        }
        catch (Exception e) {
            e.printStackTrace();
            return 0L;
        }
    }

    public static String getMarginMin(long start, long startTime) {
        return (start - startTime) / 60L + "";
    }

    public static int getWhatDay(long timeStamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeStamp * 1000L);
        int whatDay = 0;
        if (cal.get(7) == 7) {
            whatDay = 6;
        }
        if (cal.get(7) == 1) {
            whatDay = 0;
        }
        if (cal.get(7) == 2) {
            whatDay = 1;
        }
        if (cal.get(7) == 3) {
            whatDay = 2;
        }
        if (cal.get(7) == 4) {
            whatDay = 3;
        }
        if (cal.get(7) == 5) {
            whatDay = 4;
        }
        if (cal.get(7) == 6) {
            whatDay = 5;
        }
        return whatDay;
    }

    public static int getWhatDayRing(long timeStamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timeStamp * 1000L);
        int whatDay = 0;
        if (cal.get(7) == 7) {
            whatDay = 5;
        }
        if (cal.get(7) == 1) {
            whatDay = 6;
        }
        if (cal.get(7) == 2) {
            whatDay = 0;
        }
        if (cal.get(7) == 3) {
            whatDay = 1;
        }
        if (cal.get(7) == 4) {
            whatDay = 2;
        }
        if (cal.get(7) == 5) {
            whatDay = 3;
        }
        if (cal.get(7) == 6) {
            whatDay = 4;
        }
        return whatDay;
    }

    public static int getAgeByBirthday(Date birthday) {
        Calendar cal = Calendar.getInstance();
        if (cal.before(birthday)) {
            throw new IllegalArgumentException("The birthDay is before Now.It's unbelievable!");
        }
        int yearNow = cal.get(1);
        int monthNow = cal.get(2) + 1;
        int dayOfMonthNow = cal.get(5);
        cal.setTime(birthday);
        int yearBirth = cal.get(1);
        int monthBirth = cal.get(2) + 1;
        int dayOfMonthBirth = cal.get(5);
        int age = yearNow - yearBirth;
        if (monthNow <= monthBirth) {
            if (monthNow == monthBirth) {
                if (dayOfMonthNow < dayOfMonthBirth) {
                    --age;
                }
            } else {
                --age;
            }
        }
        return age;
    }

    public String fetchToDay(int day) {
        String strDay = "";
        strDay = day < 10 ? "0" + day : day + "";
        return strDay;
    }

    public static Date convertTimezone(Date sourceDate, String targetZoneId) {
        return DateUtil.convertTimezone(sourceDate, TimeZone.getTimeZone(targetZoneId));
    }

    public static Date convertTimezone(Date sourceDate, String sourceZoneId, String targetZoneId) {
        TimeZone sourceTimeZone = TimeZone.getTimeZone(sourceZoneId);
        TimeZone targetTimeZone = TimeZone.getTimeZone(targetZoneId);
        return DateUtil.convertTimezone(sourceDate, sourceTimeZone, targetTimeZone);
    }

    public static Date convertTimezone(Date localDate, TimeZone targetTimezone) {
        return DateUtil.convertTimezone(localDate, TimeZone.getDefault(), targetTimezone);
    }

    public static Date convertTimezone(Date sourceDate, TimeZone sourceTimezone, TimeZone targetTimezone) {
        Calendar calendar = Calendar.getInstance();
        long sourceTime = sourceDate.getTime();
        calendar.setTimeZone(sourceTimezone);
        calendar.setTimeInMillis(sourceTime);
        int sourceZoneOffset = calendar.get(15);
        calendar.setTimeZone(targetTimezone);
        calendar.setTimeInMillis(sourceTime);
        int targetZoneOffset = calendar.get(15);
        int targetDaylightOffset = calendar.get(16);
        long targetTime = sourceTime + (long)(targetZoneOffset + targetDaylightOffset) - (long)sourceZoneOffset;
        return new Date(targetTime);
    }

    public static enum DateFormater {
        MMdd,
        MMdd_HHmm,
        yyyyMM,
        yyyyMMdd,
        yyyyMMdd_HHmm,
        yyyyMMdd_HHmmss,
        HHmm,
        HHmmss,
        yyyyMMddHHmm,
        SyyyyMMdd,
        dFyyyyMMdd,
        dFHHmm,
        dfddMMyyy;

    }
}

