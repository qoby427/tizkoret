package com.emhillstudio.tizcoret;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

public class ShabbatSchedulerManager {
    private static final String KEY_NEXT_3HR = "next_3hr_alarm";
    private static final String KEY_NEXT_5MIN = "next_5min_alarm";
    private static final String KEY_LAST_RUN = "last_scheduling_run";
    private static final int SHABBAT_ENTRY_ID = 1001;
    private static final String DAILY_WORK_NAME = "shabbat_daily_verification";

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
    public void scheduleIfNeeded() {
        long next3hr = prefs.getLong(KEY_NEXT_3HR, -1);
        long next5min = prefs.getLong(KEY_NEXT_5MIN, -1);

        if (shouldReschedule(next3hr, next5min)) {
            forceReschedule();
        }
    }

    /** Force a full reschedule (user toggled Shabbat ON, or worker detected mismatch) */
    public void forceReschedule() {
        long[] times = computeShabbatTimes();
        long threeHourTime = times[0];
        long fiveMinuteTime = times[1];

        System.out.println("ShabbatSchedulerManager::forceReschedule: " +
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
        AlarmUtils.cancel3HourAlarm(context);
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
    }


    /** Called from BOOT_COMPLETED receiver */
    public void onBootCompleted() {
        enqueueDailyWorker();
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

        long[] times = computeShabbatTimes();
        return times[0] != next3hr || times[1] != next5min;
    }

    /** Compute this week's Shabbat times (replace with your real logic) */
    private long[] computeShabbatTimes() {
        long threeHourBefore = 0, fiveMinuteBefore = 0;
        /*
        if(BuildConfig.DEBUG) {
            threeHourBefore = c.getTimeInMillis() + 180_000L;
            fiveMinuteBefore = c.getTimeInMillis() + 300_000L;
            System.out.println("Debug ShabbatSchedulerManager::computeShabbatTimes: " +
                    UserSettings.getTimestamp(threeHourBefore) + " " +
                    UserSettings.getTimestamp(fiveMinuteBefore));
            long candle_time = HebrewUtils.computeNextCandleLighting(context);
            System.out.println("Prod ShabbatSchedulerManager::computeShabbatTimes: " +
                    UserSettings.getTimestamp(candle_time - 3 * 3600_000L) + " " +
                    UserSettings.getTimestamp(candle_time - 300_000L));
        }
        else
        */
        {
            long candle_time = HebrewUtils.computeNextCandleLighting(context);
            threeHourBefore = candle_time - 3 * 3600_000L;
            fiveMinuteBefore = candle_time - 300_000L;
            System.out.println("Prod ShabbatSchedulerManager::computeShabbatTimes: " +
                    "lighting "+ UserSettings.getTimestamp(candle_time) + " " +
                    "notif " + UserSettings.getTimestamp(threeHourBefore) + " " +
                    "alarm " + UserSettings.getTimestamp(fiveMinuteBefore));
        }
        return new long[]{threeHourBefore, fiveMinuteBefore};
    }

    private void schedule3HourAlarm(long time) {
        AlarmUtils.schedule3HourAlarm(context, time);
    }
    public void savePayload(String json) {
        prefs.edit().putString("payload", json).apply();
    }

    public String loadPayload() {
        return prefs.getString("payload", "{}");
    }
    public String computePayloadJson() {
        try {
            JSONObject base = new JSONObject();

            // ---------------------------------------------------------
            // 1. Compute candle lighting time (your real logic here)
            // ---------------------------------------------------------
            long candleLighting =
                    //BuildConfig.DEBUG ? System.currentTimeMillis() :
                    HebrewUtils.computeNextCandleLighting(context);
            System.out.println("ShabbatSchedulerManager::computePayloadJson: " + UserSettings.getTimestamp(candleLighting));
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

    // ------------------------------------------------------------
    // WORKMANAGER
    // ------------------------------------------------------------

    /** Enqueue daily verification worker (runs once per day) */
    public void enqueueDailyWorker() {
        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        ShabbatDailyWorker.class,
                        24, TimeUnit.HOURS
                ).build();

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        DAILY_WORK_NAME,
                        ExistingPeriodicWorkPolicy.UPDATE,
                        request
                );
    }

    /** Enqueue immediate worker (user toggled Shabbat ON) */
    public void enqueueImmediateWorker() {
        OneTimeWorkRequest request =
                new OneTimeWorkRequest.Builder(ShabbatImmediateWorker.class)
                        .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(
                        "shabbat_immediate",
                        ExistingWorkPolicy.REPLACE,
                        request
                );
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

