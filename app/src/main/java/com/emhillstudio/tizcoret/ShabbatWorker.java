package com.emhillstudio.tizcoret;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public class ShabbatWorker extends Worker {

    MainActivity context;
    public ShabbatWorker(@NonNull MainActivity context, @NonNull WorkerParameters params) {
        super(context, params);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        context.addNextFridayShabbatEvents();
        return Result.success();
    }

    // Call this once (e.g., in MainActivity.onCreate)
    public static void schedule(Context context) {

        // Calculate next Saturday 1:00 AM
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_WEEK, Calendar.SATURDAY);
        cal.set(Calendar.HOUR_OF_DAY, 1);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        // If time already passed today, schedule for next week
        if (cal.getTimeInMillis() < System.currentTimeMillis()) {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        }

        long initialDelay = cal.getTimeInMillis() - System.currentTimeMillis();

        PeriodicWorkRequest request =
                new PeriodicWorkRequest.Builder(
                        ShabbatWorker.class,
                        7, TimeUnit.DAYS
                )
                        .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "ShabbatWorker",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
        );
    }
}

