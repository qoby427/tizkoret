package com.emhillstudio.tizcoret;

import android.content.ContentResolver;
import android.content.ContentValues;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

public class ShabbatHelper {
    private final Context ctx;
    private final ContentResolver cr;
    public ShabbatHelper(Context c) {
        ctx = c.getApplicationContext();
        cr = ctx.getContentResolver();
    }
    public void addNextFridayShabbatEvents() {
        long candleLighting = HebrewUtils.computeNextCandleLighting(ctx);

        if(insertCalendarEvent(candleLighting,"")) {
            UserSettings.log("MainActivity::addNextFridayShabbatEvents: adding shabbat " +
                UserSettings.getLogTime(candleLighting));
        }
    }
    public boolean insertCalendarEvent(long candleLighting, String header) {
        long calendarId = getGoogleCalendarId();
        if (calendarId == -1)
            return false;

        // Format time for title
        String formatted = new SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(candleLighting);

        String title = header + "Candle Lighting – " + formatted;

        if (eventAlreadyExists(calendarId, candleLighting, title))
            return false;

        // 1. Insert event into EVENTS table
        ContentValues event = new ContentValues();
        String timeZoneId = TimeZone.getDefault().getID();

        event.put(CalendarContract.Events.TITLE, title);
        event.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        event.put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId);
        event.put(CalendarContract.Events.EVENT_END_TIMEZONE, timeZoneId);
        event.put(CalendarContract.Events.DTSTART, candleLighting);
        event.put(CalendarContract.Events.DTEND, candleLighting + 60 * 60 * 1000);

        Uri eventUri = cr.insert(CalendarContract.Events.CONTENT_URI, event);
        if (eventUri == null)
            return false;

        long eventId = Long.parseLong(eventUri.getLastPathSegment());

        // 2. Add your 5-minute reminder
        ContentValues reminder = new ContentValues();
        reminder.put(CalendarContract.Reminders.EVENT_ID, eventId);
        reminder.put(CalendarContract.Reminders.MINUTES, 5);
        reminder.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);

        cr.insert(CalendarContract.Reminders.CONTENT_URI, reminder);

        return true;
    }
    private long getGoogleCalendarId() {
        String[] projection = new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.SYNC_EVENTS
        };

        Cursor cur = cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
        );

        if (cur == null)
            return -1;

        long bestId = -1;

        while (cur.moveToNext()) {
            long id = cur.getLong(0);
            String name = cur.getString(1);
            String account = cur.getString(2);
            String type = cur.getString(3);
            String owner = cur.getString(4);
            int access = cur.getInt(5);
            int sync = cur.getInt(6);

            // Must be Google
            if (!"com.google".equals(type))
                continue;

            // Must be synced
            if (sync != 1)
                continue;

            // Must be writable
            if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)
                continue;

            // Skip holidays/birthdays
            if (name != null && name.toLowerCase().contains("holiday"))
                continue;
            if (name != null && name.toLowerCase().contains("birthday"))
                continue;

            // Prefer primary
            if (account != null && account.equals(owner)) {
                bestId = id;
                break;
            }

            // Fallback
            if (bestId == -1)
                bestId = id;
        }

        cur.close();
        return bestId;
    }
    private boolean eventAlreadyExists(long calendarId, long startUtc, String title) {
        long minuteStart = (startUtc / 60000L) * 60000L;
        long minuteEnd = minuteStart + 59999L;

        String selection =
                //CalendarContract.Events.CALENDAR_ID + "=? AND " +
                CalendarContract.Events.TITLE + "=? AND " +
                        CalendarContract.Events.DTSTART + ">=? AND " +
                        CalendarContract.Events.DTSTART + "<=?";

        String[] selectionArgs = new String[]{
                //Long.toString(calendarId),
                title,
                Long.toString(minuteStart),
                Long.toString(minuteEnd)
        };

        Cursor cur = cr.query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events._ID},
                selection,
                selectionArgs,
                null
        );

        boolean exists = (cur != null && cur.moveToFirst());
        if (cur != null) cur.close();

        return exists;
    }
}
