package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.google.gson.Gson;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public abstract class AlarmReceiver extends BroadcastReceiver {
    protected abstract void showEarly(Context context, JSONObject payload) throws JSONException;
    protected abstract void showFinal(Context context, JSONObject payload) throws JSONException;
    protected static SharedPreferences prefs;

    @Override
    public void onReceive(Context context, Intent intent) {
        prefs = context.getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);

        try {
            // ---------------------------------------------------------
            // Extract and parse payload
            // ---------------------------------------------------------
            String json = intent.getStringExtra("payload");
            if (json == null) return;

            JSONObject payload = new JSONObject(json);

            String eventType = payload.getString("event_type");

            // ---------------------------------------------------------
            // Handle NOTIFICATION event (3 hours before)
            // ---------------------------------------------------------
            if ("notification".equals(eventType)) {
                showEarly(context, payload);
            }
            // ---------------------------------------------------------
            // Handle ALARM event (5 minutes before)
            // ---------------------------------------------------------
            else if ("alarm".equals(eventType)) {
                int notifCode = ((Number) payload.get("notification_request_code")).intValue();
                if (notifCode != 0) {

                    long candleTime = payload.getLong("next_candle_time");
                    UserSettings.log("AlarmReceiver::onReceive: 5‑min alarm, candle time " + UserSettings.getTimestamp(candleTime));

                    // Cancel early notification
                    cancelNotification(context, notifCode);

                    showFinal(context, payload);

                    return;
                }
            }
            // ---------------------------------------------------------
            // Schedule next cycle
            // ---------------------------------------------------------
            if(!UserSettings.isDebug())
                scheduleNextAlarm(context, json);

        } catch (Exception e) {
            System.out.println("AlarmReceiver::onReceive: exception " + e);
        }
    }

    protected void scheduleNextAlarm(Context context, String json) throws JSONException {

        JSONObject base = new JSONObject(json);

        int entryId = base.getInt("entry_id");
        long nextCandleTime = base.getLong("next_candle_time");
        long now = System.currentTimeMillis();
        if (nextCandleTime < now + 5000) nextCandleTime = now + 60000;

        UserSettings.log("AlarmReceiver::scheduleNextAlarm: " + UserSettings.getTimestamp(nextCandleTime));

        // --- ALARM (5 minutes before) ---
        JSONObject alarmPayload = new JSONObject(base.toString());
        alarmPayload.put("event_type", "alarm");
        alarmPayload.put("request_code", entryId * 10 + 1);
        alarmPayload.put("notification_request_code", entryId * 10 + 2);
        alarmPayload.put("next_candle_time", nextCandleTime);

        schedulePendingEvent(context, alarmPayload);

        // --- NOTIFICATION (3 hours before) ---
        JSONObject notifPayload = new JSONObject(base.toString());
        notifPayload.put("event_type", "notification");
        notifPayload.put("request_code", entryId * 10 + 2);
        notifPayload.put("next_candle_time", nextCandleTime - 3 * 60 * 60 * 1000);

        schedulePendingEvent(context, notifPayload);
    }

    protected void schedulePendingEvent(Context context, JSONObject payload) throws JSONException {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            return;
        }

        long nextCandleTime = payload.getLong("next_candle_time");
        int requestCode = payload.getInt("request_code");
        String eventType = payload.getString("event_type");

        String newJson = payload.toString();

        Intent intent = new Intent(context, getClass());
        intent.putExtra("payload", newJson);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        // ----------------------------------------------------- //
        // If this is the ALARM event, cancel the earlier NOTIFICATION
        // -----------------------------------------------------
        if ("alarm".equals(eventType)) {
            int notifRequestCode = payload.getInt("notification_request_code");
            cancelNotification(context, notifRequestCode);
        }

        System.out.println("schedulePendingEvent: " + UserSettings.getTimestamp(nextCandleTime));

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextCandleTime, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, nextCandleTime, pi);
            }
        } catch (SecurityException e) {
            System.out.println("AlarmReceiver::schedulePendingEvent: exception " + e);
        }
    }
    private void cancelNotification(Context context, int notifRequestCode) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, getClass());
        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                notifRequestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );

        if (pi != null) {
            am.cancel(pi);
            pi.cancel();
        }
    }
    @SuppressLint("ScheduleExactAlarm")
    public void scheduleFinalAlarm(Context context, long triggerAt, JSONObject payload) {

        Intent intent = new Intent(context, getClass());
        intent.setAction("FINAL_ALARM");
        intent.putExtra("payload", payload.toString());

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                5000,  // unique requestCode for final alarm
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Use AlarmClockInfo so Android treats this as a user-visible alarm
        AlarmManager.AlarmClockInfo info =
                new AlarmManager.AlarmClockInfo(triggerAt, pi);

        am.setAlarmClock(info, pi);

        UserSettings.log("AlarmReceiver::scheduleFinalAlarm → scheduled at " + UserSettings.getTimestamp(triggerAt));
    }
}
