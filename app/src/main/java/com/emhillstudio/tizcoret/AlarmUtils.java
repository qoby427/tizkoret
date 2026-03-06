package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.provider.CalendarContract;

import com.google.gson.Gson;

import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

public class AlarmUtils {
    // ------------------------------------------------------------
    // PUBLIC API — schedule/cancel BOTH alarms for this event
    // ------------------------------------------------------------
    @SuppressLint("ScheduleExactAlarm")
    public static void scheduleMasterEvent(Context context, EventManager.EventInfo info, boolean after_reboot) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null)
            return;

        long trigger = one_am(info.eventTime, after_reboot);
        int reqcode = EventManager.getMasterReqCode(info);

        String json = new Gson().toJson(info);

        SharedPreferences prefs = context.getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);
        prefs.edit().putString("master"+reqcode, json).apply();

        // Build intent
        Intent intent = new Intent(context, MasterReceiver.class);
        intent.setAction("master"+reqcode);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                reqcode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Schedule master alarm
        am.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                trigger,
                pi
        );

        new ShabbatHelper(context).updateCalendarEvent(context, info);

        UserSettings.log("AlarmUtils::scheduleMasterEvent for " + info.receiverClass().getSimpleName() +
            " reqcode=" + reqcode + " for " + UserSettings.getLogTime(info.eventTime) + " at " + UserSettings.getLogTime(trigger));
    }

    @SuppressLint("ScheduleExactAlarm")
    public static void scheduleEntry(Context context, EventManager.EventInfo info) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null)
            return;
        for (EventManager.AlarmEntry  e: Arrays.asList(info.early, info.final5)) {
            UserSettings.log("AlarmUtils::scheduleEntry " + info.receiverClass().getSimpleName() + " " +
                    e.action + " req code=" + e.requestCode + " at " + UserSettings.getLogTime(e.triggerAt));
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

        for (EventManager.AlarmEntry  e: Arrays.asList(info.early, info.final5)) {
            UserSettings.log("AlarmUtils::cancelEntry " + info.receiverClass().getSimpleName() + " " +
                    e.action + " req code " + e.requestCode);

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
                UserSettings.log("AlarmUtils::cancelled entry " + e.action + " req=" + e.requestCode);
            }
        }
    }
    public static void cancelMaster(Context context, EventManager.EventInfo info) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        int reqcode = EventManager.getMasterReqCode(info);

        UserSettings.log("AlarmUtils::cancel master event reqcode=" + reqcode);

        Intent intent = new Intent(context, MasterReceiver.class);
        intent.setAction("master"+reqcode);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                reqcode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
            UserSettings.log("AlarmUtils::cancelled master entry req=" + reqcode);
        }
    }
    public static long one_am(long event, boolean after_reboot) {
        if (UserSettings.isDebug()) {
            return event - 13 * 60_000L;
        }

        long now = System.currentTimeMillis();

        // Compute the intended 1:00 AM master event time
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(event);
        cal.set(Calendar.HOUR_OF_DAY, 1);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        long masterTime = cal.getTimeInMillis();

        if (after_reboot && masterTime < now) {
            // Master event is in the past → schedule a catch-up event
            return now + 2 * 60_000L; // 2 minutes from now
        }

        return masterTime;
    }
}
