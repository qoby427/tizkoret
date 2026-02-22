package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;
import static com.emhillstudio.tizcoret.AlarmReceiver.prefs;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Arrays;

public class AlarmUtils {

    // ------------------------------------------------------------
    // PUBLIC API — schedule/cancel BOTH alarms for this event
    // ------------------------------------------------------------
    @SuppressLint("ScheduleExactAlarm")
    public static void scheduleEntry(Context context, EventManager.EventInfo info) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null)
            return;
        for (EventManager.AlarmEntry  e: Arrays.asList(info.early, info.final5)) {
            UserSettings.log("AlarmUtils::scheduleEntry " + e.action + " req code="+e.requestCode+
                    " at " + UserSettings.getLogTime(e.triggerAt));

            SharedPreferences prefs = context.getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);
            prefs.edit().putString(e.action, e.payloadJson.toString()).apply();

            Intent intent = new Intent(context, info.receiverClass());
            intent.setAction(e.action);

            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    e.requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            am.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    e.triggerAt,
                    pi
            );
        }
    }

    public static void cancelEntry(Context context, EventManager.EventInfo info) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        UserSettings.log("AlarmUtils::cancelEntry req codes " + info.early.requestCode + " " + info.final5.requestCode);

        for (EventManager.AlarmEntry  e: Arrays.asList(info.early, info.final5)) {
            Intent intent = new Intent(context, info.receiverClass());
            intent.setAction(e.action);

            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    e.requestCode,
                    intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );

            if (pi != null) {
                am.cancel(pi);
                pi.cancel();
            }
        }
    }
}
