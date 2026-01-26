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
    public static Date computeInYearDate(Date date, int years_ahead) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        JewishCalendar now = new JewishCalendar();
        JewishCalendar jc = new JewishCalendar();
        jc.setDate(cal);

        int targetYear = now.getJewishYear()+years_ahead;
        int sourceMonth = jc.getJewishMonth();
        int sourceDay = jc.getJewishDayOfMonth();

        boolean targetLeap = now.isJewishLeapYear();

        int targetMonth = sourceMonth;

        // ---- FIX ADAR / ADAR II ----
        if (sourceMonth == JewishDate.ADAR || sourceMonth == JewishDate.ADAR_II) {
            if (targetLeap) {
                targetMonth = JewishDate.ADAR_II;
            } else {
                targetMonth = JewishDate.ADAR;
            }
        }

        JewishCalendar result = new JewishCalendar();
        result.setJewishDate(targetYear, targetMonth, sourceDay);

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

        double lat = Double.parseDouble(prefs.getString("lat", "0"));
        double lon = Double.parseDouble(prefs.getString("lon", "0"));
        double elev = Double.parseDouble(prefs.getString("elev", "0"));

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

        Date sunset = zc.getSunset();
        if (sunset == null) {
            // fallback: add 7 days
            return System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000;
        }

        // 5. Candle-lighting = sunset - 18 minutes
        long candleMillis = sunset.getTime() - 18 * 60 * 1000;

        // 6. If candle-lighting already passed today, jump to next Friday
        if (candleMillis <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_MONTH, 7);
            zc.setCalendar(cal);
            sunset = zc.getSunset();
            candleMillis = sunset.getTime() - 18 * 60 * 1000;
        }

        return candleMillis;
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
