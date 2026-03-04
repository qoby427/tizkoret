package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;

import android.content.Context;
import android.content.SharedPreferences;
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
    public long computeNextCandleLighting() {
        SharedPreferences prefs = ctx.getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);

        long candleTime;
        if(UserSettings.isDebug()) {
            long last = prefs.getLong("debug_last_candle", 0);

            if (last == 0) {
                candleTime = System.currentTimeMillis() + 15 * 60_000;
                /*
                UserSettings.log("computeNextCandleLighting curr="+
                        UserSettings.getLogTime(System.currentTimeMillis()) + " candle=" + UserSettings.getLogTime(candleTime));
                */
            } else {
                candleTime = last + 15 * 60_000;
            }
            prefs.edit().putLong("debug_last_candle", candleTime).apply();
        }
        else
            candleTime = HebrewUtils.computeNextCandleLighting(ctx);
        return candleTime;
    }
    public void addNextFridayShabbatEvents() {
        long candleLighting = computeNextCandleLighting();

        if(insertCalendarEvent(candleLighting,"") == 0) {
            UserSettings.log("ShabbatHelper::addNextFridayShabbatEvents: adding shabbat " +
                UserSettings.getLogTime(candleLighting));
        }
    }
    public long insertCalendarEvent(long candleLighting, String header) {
        long calendarId = getGoogleCalendarId();
        if (calendarId == -1)
            return 0;

        // Format time for title
        String formatted = new SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(candleLighting);

        String title = header + "Candle Lighting – " + formatted;

        if (eventAlreadyExists(calendarId, candleLighting, title))
            return 0;

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
            return 0;

        long id = Long.parseLong(eventUri.getLastPathSegment());
        return id;
    }
    public long insertCalendarEvent(EventManager.EventInfo e) {
        e.eventId = insertCalendarEvent(e.eventTime, e.message());
        return e.eventId;
    }
    public static void updateCalendarEvent(Context context, EventManager.EventInfo info) {
        if (info.eventId != 0) {
            String timeZoneId = TimeZone.getDefault().getID();
            if(timeZoneId != getEventTimezone(context, info.eventId)) {
                ContentValues values = new ContentValues();
                values.put(CalendarContract.Events.DTSTART, info.eventTime);
                values.put(CalendarContract.Events.DTEND, info.eventTime + 60 * 60 * 1000);
                values.put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId);

                Uri updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, info.eventId);
                context.getContentResolver().update(updateUri, values, null, null);
            }
        }
    }
    public static String getEventTimezone(Context context, long eventId) {
        Uri uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId);

        String[] projection = new String[] {
                CalendarContract.Events.EVENT_TIMEZONE
        };

        Cursor cursor = context.getContentResolver().query(
                uri,
                projection,
                null,
                null,
                null
        );

        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getString(
                            cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE)
                    );
                }
            } finally {
                cursor.close();
            }
        }

        return "";
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
                CalendarContract.Events.CALENDAR_ID + "=? AND " +
                CalendarContract.Events.TITLE + "=? AND " +
                        CalendarContract.Events.DTSTART + ">=? AND " +
                        CalendarContract.Events.DTSTART + "<=?";

        String[] selectionArgs = new String[]{
                Long.toString(calendarId),
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
