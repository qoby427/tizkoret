package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationManagerCompat;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class AlarmReceiver extends BroadcastReceiver {
    protected abstract String channel();
    protected abstract int icon();

    protected static SharedPreferences prefs;

    @Override
    public void onReceive(Context context, Intent intent) {
        prefs = context.getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);

        try {
            String json = prefs.getString(intent.getAction(), null);
            if (json == null) return;

            JSONObject payload = new JSONObject(json);
            prefs.edit().remove(intent.getAction()).apply();

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
                // 1. Stop EarlyService (if still running)
                context.sendBroadcast(new Intent("STOP_EARLY_ALARM"));
                // 2. Remove the early notification icon
                NotificationManagerCompat.from(context).cancel(1);
                // 3. Now show the final alarm
                showFinal(context, payload);
            }

        } catch (Exception e) {
            UserSettings.log("AlarmReceiver::onReceive: exception " + e);
        }
    }
    protected void showEarly(Context context, JSONObject payload) throws JSONException {
        int requestCode = payload.getInt("request_code");
        long candleTime = payload.getLong("next_candle_time");
        String message  = payload.getString("message");

        // Must be "Shabbat" or "Yahrzeit"
        String event = payload.getString("event");

        UserSettings.log("AlarmReceiver::showEarly: " + event +
                " reqcode=" + requestCode +
                " candle=" + UserSettings.getLogTime(candleTime));

        //
        // Start EARLY alarm service with MediaPlayer
        //
        Intent svc = new Intent(context, EarlyService.class);

        svc.setAction("EARLY_ALARM");
        svc.putExtra("event", event);
        svc.putExtra("message", message);
        svc.putExtra("request_code", requestCode);
        svc.putExtra("candle_time", UserSettings.getTimestamp(candleTime));

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                UserSettings.log("AlarmReceiver::showEarly: Starting early service");
                EarlyService.clearCode(requestCode);
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            UserSettings.log("AlarmReceiver::showEarly: exception " + e);
        }
    }
    protected void showFinal(Context context, JSONObject payload) throws JSONException {
        int requestCode = payload.getInt("request_code");
        int notifCode   = payload.getInt("notification_request_code");
        long candleTime = payload.getLong("next_candle_time");
        String message  = payload.getString("message");

        // Cancel early notification
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(notifCode);

        // Must be "Shabbat" or "Yahrzeit"
        String event = payload.getString("event");

        // Start alarm service with sound
        Intent svc = new Intent(context, event.equals("Shabbat") ? ShabbatAlarmService.class : YahrzeitAlarmService.class);
        svc.setAction("ALARM");
        svc.putExtra("event", event);
        svc.putExtra("message", message);
        svc.putExtra("request_code", requestCode);
        svc.putExtra("candle_time", UserSettings.getTimestamp(candleTime));

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                UserSettings.log("AlarmReceiver::showFinal: " + event + " reqcode=" + requestCode);
                AlarmService.clearCode(requestCode);
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            UserSettings.log("AlarmReceiver::showFinal: exception " + e);
        }
    }
}
