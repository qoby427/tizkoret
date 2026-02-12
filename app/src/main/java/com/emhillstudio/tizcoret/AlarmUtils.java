package com.emhillstudio.tizcoret;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

public class AlarmUtils {

    public static final int REQ_3HR = 3001;
    public static final int REQ_5MIN = 3002;

    // ------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------
    @SuppressLint("ScheduleExactAlarm")
    public static void scheduleNotification(Context context, boolean alarm, long triggerAtMillis) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) {
            UserSettings.log("AlarmUtils alarm=" + alarm +
                " scheduleNotif time " + UserSettings.getLogTime(triggerAtMillis));
            try {
                PendingIntent pi = alarm ? get5MinutePendingIntent(context) : get3HourPendingIntent(context);
                am.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtMillis,
                        pi
                );
            } catch (JSONException ex) {
                System.out.println("schedule3HourAlarm: " + ex);
            }
        }
    }
    public static void cancelNotification(Context context, boolean alarm) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        try {
            PendingIntent pi = alarm ? get5MinutePendingIntent(context) : get3HourPendingIntent(context);
            if (am != null) {
                am.cancel(pi);
            }
        } catch (JSONException ex) {
            System.out.println("cancel3HourAlarm: " + ex);
        }
    }
    // ------------------------------------------------------------
    // INTERNAL HELPERS
    // ------------------------------------------------------------

    private static PendingIntent get3HourPendingIntent(Context context) throws JSONException {
        SharedPreferences prefs = context.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE);
        String json = prefs.getString("payload", "{}");
        JSONObject payload = new JSONObject(json.toString());
        if (UserSettings.isDebug()) {
            long candleTime = payload.getLong("next_candle_time");
            UserSettings.log("AlarmUtils::get3HourPendingIntent candle time " + UserSettings.getLogTime(candleTime));
        }
        String target = payload.getString("event_target");

        payload.put("event_type", "notification");
        String newjson = payload.toString();
        prefs.edit().putString("SHABBAT_3HR_NOTIFICATION", newjson).apply();

        Intent intent = new Intent(context, ShabbatAlarmReceiver.class);
        intent.setAction("SHABBAT_3HR_NOTIFICATION");

        return PendingIntent.getBroadcast(
                context,
                REQ_3HR,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private static PendingIntent get5MinutePendingIntent(Context context) throws JSONException {
        SharedPreferences prefs = context.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE);

        String json = prefs.getString("payload", "{}");
        JSONObject payload = new JSONObject(json.toString());

        payload.put("event_type", "alarm");
        String newjson = new String(payload.toString());
        prefs.edit().putString("SHABBAT_5MIN_ALARM", newjson).apply();
        Intent intent = new Intent(context, ShabbatAlarmReceiver.class);
        intent.setAction("SHABBAT_5MIN_ALARM");

        return PendingIntent.getBroadcast(
                context,
                REQ_5MIN,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}

