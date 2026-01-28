package com.emhillstudio.tizcoret;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public abstract class AlarmReceiver extends BroadcastReceiver {
    protected abstract void showEarly(Context context, Map<String,Object> payload);
    protected abstract void showFinal(Context context, Map<String,Object> payload);

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            // ---------------------------------------------------------
            // Extract and parse payload
            // ---------------------------------------------------------
            String json = intent.getStringExtra("payload");
            if (json == null) return;

            Map<String, Object> payload = new Gson().fromJson(json, Map.class);

            String eventType = (String) payload.get("event_type");

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

                // Cancel earlier notification
                int notifCode = ((Number) payload.get("notification_request_code")).intValue();
                cancelNotification(context, notifCode);
                showFinal(context, payload);
            }

            // ---------------------------------------------------------
            // Schedule next cycle
            // ---------------------------------------------------------
            scheduleNextAlarm(context, json);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected void scheduleNextAlarm(Context context, String json) {

        Map<String, Object> base = new Gson().fromJson(json, Map.class);

        int entryId = ((Number) base.get("entry_id")).intValue();
        long nextCandleTime = ((Number) base.get("next_candle_time")).longValue();
        long now = System.currentTimeMillis();
        if (nextCandleTime < now + 5000) nextCandleTime = now + 60000;

        // --- ALARM (5 minutes before) ---
        Map<String, Object> alarmPayload = new HashMap<>(base);
        alarmPayload.put("event_type", "alarm");
        alarmPayload.put("request_code", entryId * 10 + 1);
        alarmPayload.put("notification_request_code", entryId * 10 + 2);
        alarmPayload.put("next_candle_time", nextCandleTime);

        schedulePendingEvent(context, alarmPayload);

        // --- NOTIFICATION (3 hours before) ---
        Map<String, Object> notifPayload = new HashMap<>(base);
        notifPayload.put("event_type", "notification");
        notifPayload.put("request_code", entryId * 10 + 2);
        notifPayload.put("next_candle_time", nextCandleTime - 3 * 60 * 60 * 1000);

        schedulePendingEvent(context, notifPayload);
    }

    protected void schedulePendingEvent(Context context, Map<String, Object> payload) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            return;
        }

        long nextCandleTime = ((Number) payload.get("next_candle_time")).longValue();
        int requestCode = ((Number) payload.get("request_code")).intValue();
        String eventType = (String) payload.get("event_type");

        String newJson = new Gson().toJson(payload);

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
            int notifRequestCode = ((Number) payload.get("notification_request_code")).intValue();
            cancelNotification(context, notifRequestCode);
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextCandleTime, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, nextCandleTime, pi);
            }
        } catch (SecurityException e) {
            // Exact alarms disabled
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
}
