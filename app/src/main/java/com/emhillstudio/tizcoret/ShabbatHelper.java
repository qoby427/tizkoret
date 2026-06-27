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
    private final long calendarId;

    public ShabbatHelper(Context c) {
        ctx = c.getApplicationContext();
        cr = ctx.getContentResolver();
        calendarId = getGoogleCalendarId();
    }
    public long computeNextCandleLighting() {
        SharedPreferences prefs = ctx.getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);

        long candleTime;
        if(UserSettings.isDebug()) {
            long last = prefs.getLong("debug_last_candle", 0);
            if (last == 0) {  // after start
                candleTime = System.currentTimeMillis() + 15 * 60_000;
            } else {
                candleTime = last + 15 * 60_000;
            }
            prefs.edit().putLong("debug_last_candle", candleTime).apply();
        }
        else
            candleTime = HebrewUtils.computeNextCandleLighting(ctx);
        return candleTime;
    }
    public long insertCalendarEvent(EventManager.EventInfo e) {
        long status = eventAlreadyExists(e);
        if (status != 0)
            return status;

        String formatted = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(e.eventTime);
        String title = e.message() + "Candle Lighting – " + formatted;

        ContentValues event = new ContentValues();
        String timeZoneId = TimeZone.getDefault().getID();

        event.put(CalendarContract.Events.TITLE, title);
        event.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        event.put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId);
        event.put(CalendarContract.Events.EVENT_END_TIMEZONE, timeZoneId);
        event.put(CalendarContract.Events.DTSTART, e.eventTime);
        event.put(CalendarContract.Events.DTEND, e.eventTime + 60 * 60 * 1000);

        long eventId = 0;
        Uri eventUri = cr.insert(CalendarContract.Events.CONTENT_URI, event);
        if (eventUri != null) {
            eventId = Long.parseLong(eventUri.getLastPathSegment());

            ContentValues values = new ContentValues();
            values.put(CalendarContract.Reminders.EVENT_ID, eventId);
            values.put(CalendarContract.Reminders.MINUTES, 0);
            values.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_EMAIL);

            cr.insert(CalendarContract.Reminders.CONTENT_URI, values);
            UserSettings.log("ShabbatHelper::insertCalendarEvent: " + " eventId " + eventId + " " +
                UserSettings.getLogTime(e.eventTime));
        }
        return eventId;
    }
    public long updateCalendarEvent(EventManager.EventInfo info) {
        if (info.eventId != 0) {
            if (eventAlreadyExists(info) != -1)
                return info.eventId;

            String formatted = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(info.eventTime);
            String title = info.message() + "Candle Lighting – " + formatted;

            String timeZoneId = TimeZone.getDefault().getID();
            ContentValues values = new ContentValues();
            values.put(CalendarContract.Events.DTSTART, info.eventTime);
            values.put(CalendarContract.Events.DTEND, info.eventTime + 60 * 60 * 1000);
            values.put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId);

            Uri updateUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, info.eventId);
            ctx.getContentResolver().update(updateUri, values, null, null);

            UserSettings.log("ShabbatHelper::updateCalendarEvent: " + " eventId " + info.eventId + " " +
                    UserSettings.getLogTime(info.eventTime));

            // Detect duplicate event created by Google Calendar
            long newId = findDuplicateEvent(info);

            if (newId != 0) {
                removeCalendarEvent(info.eventId);
                info.eventId = newId;
            }
        }
        return info.eventId;
    }
    public void removeCalendarEvent(long eventId) {
        if (eventId == 0) return;

        Uri eventUri = ContentUris.withAppendedId(
                CalendarContract.Events.CONTENT_URI,
                eventId
        );

        // Query current event times
        Cursor c = ctx.getContentResolver().query(
                eventUri,
                new String[]{
                        CalendarContract.Events.DTSTART,
                        CalendarContract.Events.DTEND
                },
                null, null, null
        );

        if (c == null || !c.moveToFirst()) {
            if (c != null) c.close();
            return; // event doesn't exist
        }

        long dtStart = c.getLong(0);
        long dtEnd   = c.getLong(1);
        c.close();

        // Move 2 years into the past
        long oneYear = 365L * 2 * 24 * 60 * 60 * 1000;
        long newStart = dtStart - oneYear;
        long newEnd   = dtEnd   - oneYear;

        ContentValues values = new ContentValues();
        values.put(CalendarContract.Events.DTSTART, newStart);
        values.put(CalendarContract.Events.DTEND, newEnd);

        ctx.getContentResolver().update(eventUri, values, null, null);

        UserSettings.log("ShabbatHelper::removeCalendarEvent " + eventId + " " +
                UserSettings.getLogTime(newStart));
    }
    private long findDuplicateEvent(EventManager.EventInfo info) {
        // First get the calendar ID of the original event
        Uri originalUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, info.eventId);

        Cursor c = ctx.getContentResolver().query(
                originalUri,
                new String[]{ CalendarContract.Events.CALENDAR_ID, CalendarContract.Events.TITLE },
                null, null, null
        );

        if (c == null || !c.moveToFirst()) {
            if (c != null) c.close();
            return 0;
        }

        long calId = c.getLong(0);
        String title = c.getString(1);
        c.close();

        // Now search for events with same title + same DTSTART in same calendar
        Cursor dup = ctx.getContentResolver().query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{ CalendarContract.Events._ID },
                CalendarContract.Events.CALENDAR_ID + "=? AND " +
                        CalendarContract.Events.TITLE + "=? AND " +
                        CalendarContract.Events.DTSTART + "=?",
                new String[]{ String.valueOf(calId), title, String.valueOf(info.eventTime) },
                null
        );

        if (dup == null) return 0;

        long foundId = 0;

        if (dup.moveToFirst()) {
            do {
                long id = dup.getLong(0);

                if (id != info.eventId) {
                    foundId = id;
                    break;
                }

            } while (dup.moveToNext());
        }


        dup.close();
        return foundId;
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
            try (cursor) {
                if (cursor.moveToFirst()) {
                    return cursor.getString(
                            cursor.getColumnIndexOrThrow(CalendarContract.Events.EVENT_TIMEZONE)
                    );
                }
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
    private String extractBaseTitle(String fullTitle) {
        final String PREFIX = "Candle Lighting – ";
        int idx = fullTitle.indexOf(PREFIX);
        if (idx == -1) return fullTitle; // fallback
        return fullTitle.substring(0, idx + PREFIX.length());
    }

    public long eventAlreadyExists(EventManager.EventInfo e) {
        String formatted = new SimpleDateFormat("h:mm a", Locale.getDefault()).format(e.eventTime);
        String title = e.message().equals("") ?
                "Candle Lighting – " + formatted :
                e.message() + "Candle Lighting – ";

        String selection =
                CalendarContract.Events.CALENDAR_ID + "=? AND " +
                        CalendarContract.Events.TITLE + " LIKE ? AND " +
                        CalendarContract.Events.DTSTART + " >= ? ";

        String[] selectionArgs = new String[]{
                Long.toString(calendarId),
                title + "%",
                Long.toString(System.currentTimeMillis())
        };

        Cursor cur = cr.query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{
                        CalendarContract.Events._ID,
                        CalendarContract.Events.DTSTART
                },
                selection,
                selectionArgs,
                null
        );

        long result = 0;

        if (cur != null) {
            if (cur.moveToFirst()) {
                long eventId = cur.getLong(0);
                long existingStart = cur.getLong(1);

                if (existingStart == (e.eventTime / 1000) * 1000) {
                    // Same title, same time → skip creation
                    result = eventId;
                } else {
                    // Same title, different time → update needed
                    e.eventId = eventId;
                    result = -1;
                }
            }

            cur.close();
        }
        return result;
    }
}
