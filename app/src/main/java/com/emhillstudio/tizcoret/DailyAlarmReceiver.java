package com.emhillstudio.tizcoret;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class DailyAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        Context appContext = context.getApplicationContext();
        ShabbatSchedulerManager manager = new ShabbatSchedulerManager(appContext);
        // ---------------------------------------------------------
        // Delegate ALL logic to the unified worker engine
        // ---------------------------------------------------------
        manager.enqueueImmediateWorker();

        // ---------------------------------------------------------
        manager.scheduleNextWakeup();
    }
}
