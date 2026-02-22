package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

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
                showFinal(context, payload);
            }
        } catch (Exception e) {
            UserSettings.log("AlarmReceiver::onReceive: exception " + e);
        }
    }

    protected void showEarly(Context context, JSONObject payload) throws JSONException {
        int requestCode = payload.getInt("request_code");
        long candleTime = payload.getLong("next_candle_time");
        long notifTime  = payload.getLong("3hour_notif_time");
        String message  = payload.getString("message");
        String event    = payload.getString("event");

        Uri soundUri = event.equals("Shabbat") ? UserSettings.getShabbatRingtone(context) : UserSettings.getYahrzeitRingtone(context);

        UserSettings.log("AlarmReceiver::showEarly: " + event + " req=" + requestCode +
                " notif time " + UserSettings.getLogTime(notifTime) +
                ", candle time " + UserSettings.getLogTime(candleTime));

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, channel())
                        .setSmallIcon(icon())
                        .setContentTitle("Candle Lighting Reminder")
                        .setContentText(message)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setSound(soundUri)
                        .setAutoCancel(true);

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        nm.notify(requestCode, builder.build());
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
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            UserSettings.log("AlarmReceiver::showFinal: exception " + e);
        }
    }
}
