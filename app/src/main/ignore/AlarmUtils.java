package com.emhillstudio.tizcoret;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public class AlarmUtils {

    // ------------------------------------------------------------
    // PUBLIC API — schedule/cancel BOTH alarms for this event
    // ------------------------------------------------------------

    @SuppressLint("ScheduleExactAlarm")
    public static void scheduleEvent(Context context, EventInfo e) {
        scheduleEntry(context, e, e.early);
        scheduleEntry(context, e, e.final5);
    }

    public static void cancelEvent(Context context, EventInfo e) {
        cancelEntry(context, e, e.early);
        cancelEntry(context, e, e.final5);
    }

    // ------------------------------------------------------------
    // INTERNAL GENERIC HELPERS
    // ------------------------------------------------------------

    @SuppressLint("ScheduleExactAlarm")
    private static void scheduleEntry(Context context, EventInfo e, AlarmEntry entry) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        UserSettings.log("AlarmUtils schedule " + entry.action +
                " at " + UserSettings.getLogTime(entry.triggerAt));

        Intent intent = new Intent(context, e.receiverClass());
        intent.setAction(entry.action);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                entry.requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                entry.triggerAt,
                pi
        );
    }

    private static void cancelEntry(Context context, EventInfo e, AlarmEntry entry) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, e.receiverClass());
        intent.setAction(entry.action);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                entry.requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pi != null) {
            UserSettings.log("AlarmUtils cancel " + entry.action);
            am.cancel(pi);
            pi.cancel();
        }
    }
}
