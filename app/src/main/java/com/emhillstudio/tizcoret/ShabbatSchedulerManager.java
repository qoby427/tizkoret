package com.emhillstudio.tizcoret;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONObject;

import java.util.Calendar;
import java.util.TimeZone;

public class ShabbatSchedulerManager {
    private static final String KEY_NEXT_3HR = "next_3hr_alarm";
    private static final String KEY_NEXT_5MIN = "next_5min_alarm";
    private static final String KEY_LAST_RUN = "last_scheduling_run";
    private static final String DAILY_WORK_NAME = "daily_work";

    private static final int SHABBAT_ENTRY_ID = 1001;
    private static boolean first_time = true;

    private final Context context;
    private final SharedPreferences prefs;

    public ShabbatSchedulerManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.prefs = this.context.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE);
    }

    // ------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------

    /** Called when user enables Shabbat or when app opens */
    public boolean scheduleIfNeeded() {
        long next3hr = prefs.getLong(KEY_NEXT_3HR, -1);
        long next5min = prefs.getLong(KEY_NEXT_5MIN, -1);

        if (shouldReschedule(next3hr, next5min)) {
            forceReschedule();
            return true;
        }
        return false;
    }

    /** Force a full reschedule (user toggled Shabbat ON, or worker detected mismatch) */
    public void forceReschedule() {
        long[] times = computeShabbatTimes();
        long threeHourTime = times[0];
        long fiveMinuteTime = times[1];

        UserSettings.log("ShabbatSchedulerManager::forceReschedule " +
                UserSettings.getTimestamp(threeHourTime) + " " +
                UserSettings.getTimestamp(fiveMinuteTime));

        schedule3HourAlarm(threeHourTime);
        schedule5MinuteAlarm(fiveMinuteTime);

        prefs.edit()
                .putLong(KEY_NEXT_3HR, threeHourTime)
                .putLong(KEY_NEXT_5MIN, fiveMinuteTime)
                .putLong(KEY_LAST_RUN, System.currentTimeMillis())
                .apply();
    }

    /** Cancel all alarms (if user disables Shabbat) */
    public void cancelAll() {
        // 1. Cancel alarms
        AlarmUtils.cancel3HourNotif(context);
        AlarmUtils.cancel5MinuteAlarm(context);

        // 2. Clear stored timestamps
        prefs.edit()
                .remove(KEY_NEXT_3HR)
                .remove(KEY_NEXT_5MIN)
                .apply();

        // 3. Stop daily worker
        WorkManager.getInstance(context)
                .cancelUniqueWork("shabbat_daily_verification");

        // 4. Stop immediate worker (if one is pending)
        WorkManager.getInstance(context)
                .cancelUniqueWork("shabbat_immediate");
        stopDailyAlarm();
    }


    /** Called from BOOT_COMPLETED receiver */
    public void onBootCompleted() {
        enqueueWorker();
        scheduleIfNeeded();
    }

    /** Exposed for debug UI */
    public ShabbatStatus getStatus() {
        return new ShabbatStatus(
                prefs.getLong(KEY_NEXT_3HR, -1),
                prefs.getLong(KEY_NEXT_5MIN, -1),
                prefs.getLong(KEY_LAST_RUN, -1)
        );
    }

    // ------------------------------------------------------------
    // INTERNAL LOGIC
    // ------------------------------------------------------------

    private boolean shouldReschedule(long next3hr, long next5min) {
        if (next3hr <= 0 || next5min <= 0) return true;

        long now = System.currentTimeMillis();
        if (next3hr < now || next5min < now) return true;

        if (!alarmExists(next3hr)) return true;
        if (timezoneChanged()) return true;
        if (locationChanged()) return true;

        long[] times = computeShabbatTimes();
        return times[0] != next3hr || times[1] != next5min;
    }
    private boolean alarmExists(long triggerTime) {
        Intent intent = new Intent(context, ShabbatAlarmReceiver.class);
        intent.setAction("SHABBAT_ALARM");

        // requestCode must match your scheduling logic
        int requestCode = (int) (triggerTime % Integer.MAX_VALUE);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        return pi != null;
    }
    private boolean timezoneChanged() {
        String savedTz = prefs.getString("saved_timezone", null);
        String currentTz = TimeZone.getDefault().getID();

        if (savedTz == null) return true; // first run or corrupted prefs
        return !savedTz.equals(currentTz);
    }
    private boolean locationChanged() {
        double savedLat = Double.longBitsToDouble(prefs.getLong("saved_lat", 0));
        double savedLng = Double.longBitsToDouble(prefs.getLong("saved_lng", 0));

        double lat = UserSettings.getLatitude(context);
        double lng = UserSettings.getLongitude(context);


        if (savedLat == 0 || savedLng == 0) return true;

        float[] result = new float[1];
        Location.distanceBetween(savedLat, savedLng, lat, lng, result);

        // If moved more than ~10 km, recompute
        return result[0] > 10000;
    }

    private long[] computeShabbatTimes() {
        long threeHourBefore = 0, fiveMinuteBefore = 0;

        if(UserSettings.isDebug()) {
            Calendar c = Calendar.getInstance();
            threeHourBefore = c.getTimeInMillis() + 180_000L;
            fiveMinuteBefore = c.getTimeInMillis() + 300_000L;
            UserSettings.log( "ShabbatSchedulerManager::computeShabbatTimes " +
                    UserSettings.getTimestamp(threeHourBefore) + " " +
                    UserSettings.getTimestamp(fiveMinuteBefore));
            long candle_time = HebrewUtils.computeNextCandleLighting(context);
            UserSettings.log( "ShabbatSchedulerManager::computeShabbatTimes " +
                    UserSettings.getTimestamp(candle_time - 3 * 3600_000L) + " " +
                    UserSettings.getTimestamp(candle_time - 300_000L));
        }
        else

        {
            long candle_time = HebrewUtils.computeNextCandleLighting(context);
            threeHourBefore = candle_time - 3 * 3600_000L;
            fiveMinuteBefore = candle_time - 300_000L;
            UserSettings.log( "ShabbatSchedulerManager::computeShabbatTimes " +
                    "lighting "+ UserSettings.getTimestamp(candle_time) + " " +
                    "notif " + UserSettings.getTimestamp(threeHourBefore) + " " +
                    "alarm " + UserSettings.getTimestamp(fiveMinuteBefore));
        }
        return new long[]{threeHourBefore, fiveMinuteBefore};
    }

    private void schedule3HourAlarm(long time) {
        AlarmUtils.schedule3HourNotif(context, time);
    }
    public void savePayload(String json) {
        prefs.edit().putString("payload", json).apply();
    }

    public String loadPayload() {
        return prefs.getString("payload", "{}");
    }
    public String computePayloadJson() {
        prefs.edit().
            putString("saved_timezone", TimeZone.getDefault().getID()).
            putLong("saved_lat", Double.doubleToRawLongBits(UserSettings.getLatitude(context))).
            putLong("saved_lng", Double.doubleToRawLongBits(UserSettings.getLongitude(context))).apply();

        try {
            JSONObject base = new JSONObject();

            // ---------------------------------------------------------
            // 1. Compute candle lighting time (your real logic here)
            // ---------------------------------------------------------
            long candleLighting =
                    UserSettings.isDebug() ? System.currentTimeMillis() :
                    HebrewUtils.computeNextCandleLighting(context);
            UserSettings.log("ShabbatSchedulerManager::computePayloadJson " + UserSettings.getTimestamp(candleLighting));
            // ---------------------------------------------------------
            // 3. Base payload (NO event_type here!)
            // ---------------------------------------------------------
            base.put("request_code", SHABBAT_ENTRY_ID);
            base.put("next_candle_time", candleLighting);
            base.put("notification_request_code", SHABBAT_ENTRY_ID);

            long[] alarm_times = computeShabbatTimes();
            base.put("3hour_notif_time", alarm_times[0]);
            base.put("5min_alarm_time", alarm_times[1]);

            return base.toString();

        } catch (Exception e) {
            System.out.println("ShabbatSchedulerManager::computePayloadJson: \n" + e);
            return "{}";
        }
    }
    private void schedule5MinuteAlarm(long millis) {
        AlarmUtils.schedule5MinuteAlarm(context, millis);
    }
    public void enqueueWorker() {
        scheduleNextWakeup();
    }
    public void enqueueImmediateWorker() {
        long last = prefs.getLong("last_processed_shabbat", 0);
        if(last > System.currentTimeMillis()) {
            UserSettings.log("Shabbat at " + UserSettings.getTimestamp(last) + " processed: skipping  daily worker");
            return;
        }

        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(ShabbatDailyWorker.class)
                        .addTag(ShabbatDailyWorker.TAG)
                        .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        "shabbat_immediate",
                        ExistingWorkPolicy.REPLACE,
                        request
                );
    }
    @SuppressLint("ScheduleExactAlarm")
    public void scheduleNextWakeup() {
        long triggerAt = computeNextDailyTrigger();

        UserSettings.log("Scheduling wakeup at " + UserSettings.getTimestamp(triggerAt));

        Intent intent = new Intent(context, DailyAlarmReceiver.class);
        intent.setAction("DAILY_VERIFICATION");

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                9999, // fixed ID for daily alarm
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
    }
    public void stopDailyAlarm() {
        Intent intent = new Intent(context, DailyAlarmReceiver.class);
        intent.setAction("DAILY_VERIFICATION");

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                9999,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pi != null) {
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            am.cancel(pi);
            pi.cancel();
            UserSettings.log("stopDailyAlarm(): Daily alarm cancelled");
        } else {
            UserSettings.log("stopDailyAlarm(): No daily alarm to cancel");
        }
    }

    private long computeNextDailyTrigger() {
        Calendar c = Calendar.getInstance();

        if (UserSettings.isDebug()) {
            // Fire every 15 minutes in debug mode
            c.add(Calendar.MINUTE, first_time ? 1 : 15);
            first_time = false;
            return c.getTimeInMillis();
        }

        // ---- RELEASE MODE ----
        // Fire once per day at 03:00 AM local time
        c.set(Calendar.HOUR_OF_DAY, 3);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);

        long now = System.currentTimeMillis();
        long trigger = c.getTimeInMillis();

        // If 3 AM already passed today → schedule for tomorrow
        if (trigger <= now) {
            c.add(Calendar.DAY_OF_YEAR, 1);
            trigger = c.getTimeInMillis();
        }

        return trigger;
    }

    // ------------------------------------------------------------
    // STATUS CLASS
    // ------------------------------------------------------------

    public static class ShabbatStatus {
        public final long next3hr;
        public final long next5min;
        public final long lastRun;

        public ShabbatStatus(long next3hr, long next5min, long lastRun) {
            this.next3hr = next3hr;
            this.next5min = next5min;
            this.lastRun = lastRun;
        }
    }
}

