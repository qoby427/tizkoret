package com.emhillstudio.tizcoret;

import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.hebrewcalendar.JewishDate;

import java.util.Calendar;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

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

    public static String formatHebrewDayMonth(JewishCalendar jc) {
        HebrewDateFormatter f = new HebrewDateFormatter();
        f.setHebrewFormat(true);

        // Day as Hebrew numeral (e.g., י״ד)
        String day = f.formatHebrewNumber(jc.getJewishDayOfMonth());

        // Month name (e.g., טבת)
        String month = f.formatMonth(jc); // <-- THIS is the correct API
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
}
