package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import com.kosherjava.zmanim.ZmanimCalendar;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;
import com.kosherjava.zmanim.util.GeoLocation;

import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class HebrewUtils {

    private static final HebrewDateFormatter formatter = new HebrewDateFormatter();

    static {
        formatter.setHebrewFormat(true); // Hebrew letters, not transliteration
    }

    // Convert civil Date → Hebrew date string
    public static String toHebrewDate(Date date) {
        if (date == null) return "";

        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        JewishCalendar jc = new JewishCalendar();
        jc.setDate(cal);

        return formatHebrewDayMonthEnglish(jc);
    }
    public static String formatHebrewDayMonthEnglish(JewishCalendar jc) {
        HebrewDateFormatter fmt = new HebrewDateFormatter();
        fmt.setHebrewFormat(false); // English transliteration

        String day = String.valueOf(jc.getJewishDayOfMonth());
        String month = fmt.formatMonth(jc);

        return day + " " + month;
    }
    public static Date nextYahrzeit(Date deathGregorian) {

        // 1️⃣ Hebrew date of death
        JewishCalendar deathJC = new JewishCalendar();
        deathJC.setDate(deathGregorian);

        int deathDay = deathJC.getJewishDayOfMonth();
        int deathMonth = deathJC.getJewishMonth();

        // 2️⃣ Current Hebrew year
        JewishCalendar nowJC = new JewishCalendar();
        nowJC.setDate(new Date());
        int currentHebrewYear = nowJC.getJewishYear();

        // 3️⃣ Try this year and next year
        Date candidate1 = yahrzeitInHebrewYear(currentHebrewYear, deathMonth, deathDay);

        if (candidate1.after(new Date())) {
            return candidate1;
        }

        return yahrzeitInHebrewYear(currentHebrewYear + 1, deathMonth, deathDay);
    }
    private static Date yahrzeitInHebrewYear(int hebrewYear, int month, int day) {
        JewishCalendar jc = new JewishCalendar();
        if (month == JewishDate.ADAR || month == JewishDate.ADAR_II) {
            boolean targetLeap = jc.isJewishLeapYear();
            if (targetLeap) {
                month = JewishDate.ADAR_II;
            } else {
                month = JewishDate.ADAR;
            }
        }

        jc.setJewishDate(hebrewYear, month, day);

        Calendar gc = jc.getGregorianCalendar();
        return gc.getTime();
    }
    public static Date computeInYearDate(Date date, int years_ahead) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        JewishCalendar now = new JewishCalendar();
        JewishCalendar jc = new JewishCalendar();
        jc.setDate(cal);

        int targetYear = now.getJewishYear()+years_ahead;
        int sourceMonth = jc.getJewishMonth();
        int sourceDay = jc.getJewishDayOfMonth();

        int targetMonth = sourceMonth;

        if (sourceMonth == JewishDate.ADAR || sourceMonth == JewishDate.ADAR_II) {
            boolean targetLeap = now.isJewishLeapYear();
            if (targetLeap) {
                targetMonth = JewishDate.ADAR_II;
            } else {
                targetMonth = JewishDate.ADAR;
            }
        }

        JewishCalendar rosh_hashana = new JewishCalendar();
        rosh_hashana.setJewishDate(targetYear, 7, 1);
        JewishCalendar result = new JewishCalendar();
        result.setJewishDate(targetYear, targetMonth, sourceDay);
        if(result.getAbsDate() >= rosh_hashana.getAbsDate())
            result.setJewishDate(targetYear+1, targetMonth, sourceDay);

        Date gregorian = result.getGregorianCalendar().getTime();
        return gregorian;
    }
    public static String computeInYear(Date date) {
        Date gregorian = computeInYearDate(date,0);
        SimpleDateFormat fmt = new SimpleDateFormat("MMM dd", Locale.US);
        return fmt.format(gregorian);
    }

    public static long computeNextCandleLighting(Context context) {
        // 1. Load location
        SharedPreferences prefs = context.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE);

        double lat = UserSettings.getLatitude(context);
        double lon = UserSettings.getLongitude(context);
        double elev = 300;

        // 2. Start from NOW
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // 3. Move forward until we hit Friday
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        // 4. Compute sunset for that Friday
        ZmanimCalendar zc = new ZmanimCalendar();
        zc.setGeoLocation(new GeoLocation("loc", lat, lon, elev, TimeZone.getDefault()));
        zc.setCalendar(cal);

        // 5. Next candle lighting

        Date candleMillis = zc.getCandleLighting();

        // 6. If candle-lighting already passed today, jump to next Friday
        if (candleMillis.getTime() - 12 * 60_000 <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 7);
            zc.setCalendar(cal);
            candleMillis = zc.getCandleLighting();
        }

        return candleMillis.getTime();
    }

    public static long computeNextYahrzeit(Context context, Date diedDate) {

        // 1. Convert original date of death to Hebrew date
        JewishCalendar jc = new JewishCalendar();
        jc.setDate(diedDate);

        int hMonth = jc.getJewishMonth();
        int hDay = jc.getJewishDayOfMonth();

        // 2. Compute next Hebrew year
        int nextHebrewYear = jc.getJewishYear() + 1;

        // 3. Create a new Hebrew date for next year's Yahrzeit
        JewishCalendar next = new JewishCalendar();
        next.setJewishYear(nextHebrewYear);
        // ---- FIX ADAR / ADAR II ----
        if (hMonth == JewishDate.ADAR || hMonth == JewishDate.ADAR_II) {
            if (next.isJewishLeapYear()) {
                hMonth = JewishDate.ADAR_II;
            } else {
                hMonth = JewishDate.ADAR;
            }
        }
        next.setJewishDate(nextHebrewYear, hMonth, hDay);

        // 4. Convert to Gregorian millis
        Calendar cal = Calendar.getInstance();
        cal.set(next.getGregorianYear(), next.getGregorianMonth(), next.getGregorianDayOfMonth(),
                0, 0, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long trigger = cal.getTimeInMillis();

        // 5. Apply your offset (5 minutes before)
        long local = trigger - 5 * 60 * 1000;

        return local;
    }
}
