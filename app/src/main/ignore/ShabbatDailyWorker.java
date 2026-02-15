package com.emhillstudio.tizcoret;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ShabbatDailyWorker extends Worker {
    public final static String TAG = "TizcoretDailyWorker";
    private EventManager manager;
    private Context ctx;
    public ShabbatDailyWorker(@NonNull Context context,  @NonNull WorkerParameters params) {
        super(context, params);
        ctx = context.getApplicationContext();
        manager = new EventManager(ctx);
    }

    @NonNull
    @Override
    public Result doWork() {
        if(!getTags().contains(TAG)) {
            UserSettings.log("ShabbatDailyWorker::doWork Ignored worker tags: " + getTags());
            return Result.success();
        }

        long now = System.currentTimeMillis();
        SharedPreferences prefs = ctx.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE);
        prefs.edit().putLong("last_daily_worker", now).apply();
        UserSettings.log("ShabbatDailyWorker executed at " +
            UserSettings.getLogTime(now) + " in Debug = " + UserSettings.isDebug());

        manager.scheduleIfNeeded();
        UserSettings.log("ShabbatDailyWorker scheduleIfNeeded() finished");

        return Result.success();
    }
}

