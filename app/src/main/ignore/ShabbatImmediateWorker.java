package com.emhillstudio.tizcoret;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ShabbatImmediateWorker extends Worker {

    public ShabbatImmediateWorker(
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

        // Compute JSON payload
        String json = manager.computePayloadJson();
        manager.savePayload(json);

        // Schedule alarms (5 min + 1 hour)
        manager.forceReschedule();

        return Result.success();
    }
}

