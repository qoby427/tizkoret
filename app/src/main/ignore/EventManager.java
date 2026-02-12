package com.emhillstudio.tizcoret;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

public class EventManager {

    private static final int SHABBAT_ENTRY_ID = 1001;
    private static final int YAHRZEIT_ENTRY_ID = 2001;

    private static final String KEY_PAYLOAD_SHABBAT = "payload_shabbat";
    private static final String KEY_PAYLOAD_YAHRZEIT = "payload_yahrzeit";

    private static final String KEY_NEXT_3HR_SHABBAT = "next_3hr_shabbat";
    private static final String KEY_NEXT_5MIN_SHABBAT = "next_5min_shabbat";

    private static final String KEY_NEXT_3HR_YAHRZEIT = "next_3hr_yahrzeit";
    private static final String KEY_NEXT_5MIN_YAHRZEIT = "next_5min_yahrzeit";

    private final Context ctx;
    private final SharedPreferences prefs;

    public EventManager(@NonNull Context context) {
        this.ctx = context.getApplicationContext();
        this.prefs = ctx.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE);
    }

    // --------------------------------------------------------------------
    // PUBLIC API
    // --------------------------------------------------------------------

    /** Called by Worker and BootReceiver */
    public boolean scheduleIfNeeded() {
        boolean changed = false;

        List<EventInfo> events = detectEventsForToday();

        for (EventInfo e : events) {
            if (shouldReschedule(e)) {
                forceReschedule(e);
                changed = true;
            }
        }

        return changed;
    }

    /** Cancel all alarms (user disables Shabbat/Yahrzeit) */
    public void cancelAll() {
        AlarmUtils.cancelNotification(ctx, false);
        AlarmUtils.cancelNotification(ctx, true);

        prefs.edit()
                .remove(KEY_NEXT_3HR_SHABBAT)
                .remove(KEY_NEXT_5MIN_SHABBAT)
                .remove(KEY_NEXT_3HR_YAHRZEIT)
                .remove(KEY_NEXT_5MIN_YAHRZEIT)
                .remove(KEY_PAYLOAD_SHABBAT)
                .remove(KEY_PAYLOAD_YAHRZEIT)
                .apply();
    }

    /** Trigger immediate Worker run */
    public void enqueueImmediateWorker() {
        OneTimeWorkRequest req =
                new OneTimeWorkRequest.Builder(ShabbatDailyWorker.class)
                        .addTag(ShabbatDailyWorker.TAG)
                        .build();

        WorkManager.getInstance(ctx)
                .enqueueUniqueWork("event_immediate",
                        ExistingWorkPolicy.REPLACE,
                        req);
    }

    // --------------------------------------------------------------------
    // EVENT DETECTION
    // --------------------------------------------------------------------

    private List<EventInfo> detectEventsForToday() {
        List<EventInfo> list = new ArrayList<>();

        Calendar cal = Calendar.getInstance();
        int dow = cal.get(Calendar.DAY_OF_WEEK);

        // SHABBAT
        if (dow == Calendar.FRIDAY) {
            EventInfo sh = new EventInfo();
            sh.type = EventType.SHABBAT;
            sh.requestCodeBase = SHABBAT_ENTRY_ID;
            sh.eventTime = HebrewUtils.computeNextCandleLighting(ctx);
            sh.message = "Shabbat begins soon";
            list.add(sh);
        }

        // YAHRZEIT
        List<YahrzeitEntry> all = UserSettings.loadYahrzeitList(ctx);
        List<String> names = new ArrayList<>();
/*
        String todayStr = UserSettings.DATE_ONLY.format(cal.getTime());

        for (YahrzeitEntry entry : all) {
            String entryStr = UserSettings.DATE_ONLY.format(entry.inYear);
            if (entryStr.equals(todayStr)) {
                names.add(entry.name);
            }
        }
*/
        if (!names.isEmpty()) {
            EventInfo yz = new EventInfo();
            yz.type = EventType.YAHRZEIT;
            yz.requestCodeBase = YAHRZEIT_ENTRY_ID;
            yz.eventTime = HebrewUtils.computeNextCandleLighting(ctx);
            yz.message = "Yahrzeit of " + String.join(", ", names) + " is tomorrow";
            list.add(yz);
        }

        return list;
    }

    // --------------------------------------------------------------------
    // RESCHEDULING LOGIC
    // --------------------------------------------------------------------

    private boolean shouldReschedule(EventInfo e) {
        long next3hr = prefs.getLong(e.key3hr(), -1);
        long next5min = prefs.getLong(e.key5min(), -1);

        if (next3hr <= 0 || next5min <= 0) return true;

        long now = System.currentTimeMillis();
        if (next3hr < now || next5min < now) return true;

        if (timezoneChanged()) return true;
        if (locationChanged()) return true;

        long[] times = computeEventTimes(e.eventTime);
        return times[0] != next3hr || times[1] != next5min;
    }

    private boolean timezoneChanged() {
        String saved = prefs.getString("saved_timezone", null);
        String current = TimeZone.getDefault().getID();
        return saved == null || !saved.equals(current);
    }

    private boolean locationChanged() {
        double savedLat = Double.longBitsToDouble(prefs.getLong("saved_lat", 0));
        double savedLng = Double.longBitsToDouble(prefs.getLong("saved_lng", 0));

        double lat = UserSettings.getLatitude(ctx);
        double lng = UserSettings.getLongitude(ctx);

        if (savedLat == 0 || savedLng == 0) return true;

        float[] result = new float[1];
        android.location.Location.distanceBetween(savedLat, savedLng, lat, lng, result);
        return result[0] > 10000; // 10 km
    }

    // --------------------------------------------------------------------
    // RESCHEDULING
    // --------------------------------------------------------------------

    private void forceReschedule(EventInfo e) {
        long[] times = computeEventTimes(e.eventTime);
        long early = times[0];
        long final5 = times[1];

        scheduleEvent(e, early, final5);

        prefs.edit()
                .putLong(e.key3hr(), early)
                .putLong(e.key5min(), final5)
                .putString(e.keyPayload(), computePayloadJson(e, early, final5))
                .putString("saved_timezone", TimeZone.getDefault().getID())
                .putLong("saved_lat", Double.doubleToRawLongBits(UserSettings.getLatitude(ctx)))
                .putLong("saved_lng", Double.doubleToRawLongBits(UserSettings.getLongitude(ctx)))
                .apply();
    }

    private void scheduleEvent(EventInfo e, long early, long final5) {
        if (e.type == EventType.SHABBAT) {
            AlarmUtils.scheduleNotification(ctx, false, early);
            AlarmUtils.scheduleNotification(ctx, true, final5);
        } else {
            //AlarmUtils.scheduleYahrzeit3HourNotif(ctx, early);
            //AlarmUtils.scheduleYahrzeit5MinuteAlarm(ctx, final5);
        }
    }

    private long[] computeEventTimes(long eventTime) {
        long early = eventTime - 3 * 3600_000L;
        long final5 = eventTime - 300_000L;
        return new long[]{early, final5};
    }

    // --------------------------------------------------------------------
    // JSON PAYLOAD
    // --------------------------------------------------------------------

    private String computePayloadJson(EventInfo e, long early, long final5) {
        try {
            JSONObject base = new JSONObject();
            base.put("event_target", e.type.name());
            base.put("event_time", e.eventTime);
            base.put("message", e.message);
            base.put("request_code", e.requestCodeBase);
            base.put("3hour_notif_time", early);
            base.put("5min_alarm_time", final5);
            return base.toString();
        } catch (Exception ex) {
            return "{}";
        }
    }

    // --------------------------------------------------------------------
    // INTERNAL CLASSES
    // --------------------------------------------------------------------

    private enum EventType { SHABBAT, YAHRZEIT }

    private static class EventInfo {
        EventType type;
        long eventTime;
        String message;
        int requestCodeBase;

        String keyPayload() {
            return type == EventType.SHABBAT ? KEY_PAYLOAD_SHABBAT : KEY_PAYLOAD_YAHRZEIT;
        }

        String key3hr() {
            return type == EventType.SHABBAT ? KEY_NEXT_3HR_SHABBAT : KEY_NEXT_3HR_YAHRZEIT;
        }

        String key5min() {
            return type == EventType.SHABBAT ? KEY_NEXT_5MIN_SHABBAT : KEY_NEXT_5MIN_YAHRZEIT;
        }
    }
}
