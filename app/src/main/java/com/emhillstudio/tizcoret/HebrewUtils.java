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

        return formatHebrewDayMonth(jc);
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
    public static String computeInYear(Date date) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);

        JewishCalendar now = new JewishCalendar();
        JewishCalendar jc = new JewishCalendar();
        jc.setDate(cal);

        int targetYear = now.getJewishYear();
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
        SimpleDateFormat fmt = new SimpleDateFormat("MMM dd", Locale.US);
        return fmt.format(gregorian);
    }

    public static String sameDayMonthThisYear(Date original) {
        Calendar src = Calendar.getInstance();
        src.setTime(original);

        int day = src.get(Calendar.DAY_OF_MONTH);
        int month = src.get(Calendar.MONTH); // 0‑based

        Calendar now = Calendar.getInstance();
        int currentYear = now.get(Calendar.YEAR);

        Calendar result = Calendar.getInstance();
        result.set(Calendar.YEAR, currentYear);
        result.set(Calendar.MONTH, month);
        result.set(Calendar.DAY_OF_MONTH, day);

        // Reset time to midnight if you want
        result.set(Calendar.HOUR_OF_DAY, 0);
        result.set(Calendar.MINUTE, 0);
        result.set(Calendar.SECOND, 0);
        result.set(Calendar.MILLISECOND, 0);

        Date date = result.getTime();
        SimpleDateFormat fmt = new SimpleDateFormat("MMM dd", Locale.US);
        return fmt.format(date);
    }
}
