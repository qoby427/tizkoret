package com.emhillstudio.tizcoret;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ShabbatDailyWorker extends Worker {

    public ShabbatDailyWorker(
            @NonNull Context context,
            @NonNull WorkerParameters params
    ) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context ctx = getApplicationContext();
        ShabbatSchedulerManager manager = new ShabbatSchedulerManager(ctx);

        // Recompute and update payload
        String json = manager.computePayloadJson();
        manager.savePayload(json);

        // Self-heal alarms
        manager.scheduleIfNeeded();

        return Result.success();
    }
}

