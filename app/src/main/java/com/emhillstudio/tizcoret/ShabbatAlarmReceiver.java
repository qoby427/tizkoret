package com.emhillstudio.tizcoret;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class ShabbatAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        // -----------------------------
        // 1. Do your alarm action
        // -----------------------------
        Intent serviceIntent = new Intent(context, ShabbatAlarmService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        }

        // -----------------------------
        // 2. Schedule the next alarm
        // -----------------------------
        scheduleNextAlarm(context);
    }

    private void scheduleNextAlarm(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Check exact alarm permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !am.canScheduleExactAlarms()) {
            // You can show a notification or toast here if you want
            return;
        }

        // Next alarm in 12 hours
        long nextTrigger = System.currentTimeMillis() + 12 * 60 * 60 * 1000;

        // Save for UI if needed
        UserSettings.setNextAlarmTime(context, nextTrigger);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                1001,
                new Intent(context, ShabbatAlarmReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, nextTrigger, pi);
            }
        } catch (SecurityException e) {
            // Exact alarms disabled
        }
    }
}
