package com.emhillstudio.tizcoret;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public abstract class AlarmReceiver extends BroadcastReceiver {
    protected void scheduleNextAlarm(Context context, String json) {

        Map<String, Object> base = new Gson().fromJson(json, Map.class);

        long nextCandleTime = ((Number) base.get("next_candle_time")).longValue();
        long now = System.currentTimeMillis();
        if (nextCandleTime < now + 5000) nextCandleTime = now + 60000;

        // --- ALARM (5 minutes before) ---
        Map<String, Object> alarmPayload = new HashMap<>(base);
        alarmPayload.put("event_type", "alarm");
        alarmPayload.put("offset", 1);
        alarmPayload.put("next_candle_time", nextCandleTime);

        schedulePendingEvent(context, alarmPayload);

        // --- NOTIFICATION (3 hours before) ---
        Map<String, Object> notifPayload = new HashMap<>(base);
        notifPayload.put("event_type", "notification");
        notifPayload.put("offset", 2);
        notifPayload.put("next_candle_time", nextCandleTime - 3 * 60 * 60 * 1000);

        schedulePendingEvent(context, notifPayload);
    }

    protected void schedulePendingEvent(Context context, Map<String, Object> payload) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            return;
        }

        int entryId = ((Number) payload.get("entry_id")).intValue();
        long nextCandleTime = ((Number) payload.get("next_candle_time")).longValue();
        int offset = ((Number) payload.get("offset")).intValue();
        int requestCode = entryId * 10 + offset;

        String newJson = new Gson().toJson(payload);

        Intent intent = new Intent(context, getClass());
        intent.putExtra("payload", newJson);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

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
}
