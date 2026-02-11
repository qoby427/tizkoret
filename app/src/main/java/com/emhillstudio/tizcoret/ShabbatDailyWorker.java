package com.emhillstudio.tizcoret;

import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ShabbatDailyWorker extends Worker {
    public final static String TAG = "Tizcoret Daily Worker";
    private ShabbatSchedulerManager manager;
    private Context ctx;
    public ShabbatDailyWorker(@NonNull Context context,  @NonNull WorkerParameters params) {
        super(context, params);
        ctx = context.getApplicationContext();
        manager = new ShabbatSchedulerManager(ctx);
    }

    @NonNull
    @Override
    public Result doWork() {
        if(!getTags().contains(TAG)) {
            UserSettings.log("ShabbatDailyWorker::doWork Ignored worker tags: " + getTags());
            return Result.success();
        }

        Location loc = LocationHelper.getWorkerLocation(ctx);
        if(loc != null) {
            UserSettings.setLatitude(ctx, loc.getLatitude());
            UserSettings.setLongitude(ctx, loc.getLongitude());

            UserSettings.log("ShabbatDailyWorker got location: " + loc.getLatitude() + ", " + loc.getLongitude());
        }

        long now = System.currentTimeMillis();
        SharedPreferences prefs = ctx.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE);
        prefs.edit().putLong("last_daily_worker", now).apply();
        UserSettings.log("ShabbatDailyWorker executed at " +
            UserSettings.getTimestamp(now) + " in Debug = " + UserSettings.isDebug());

        // 2. Recompute JSON (canonical source of truth)
        String json = manager.computePayloadJson();
        manager.savePayload(json);
        UserSettings.log("ShabbatDailyWorker recomputed and saved JSON payload");

        // 3. Self-heal alarms (only if needed)
        boolean repaired = manager.scheduleIfNeeded();
        UserSettings.log("ShabbatDailyWorker scheduleIfNeeded() result = " + repaired);

        return Result.success();
    }
}

