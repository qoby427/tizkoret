package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
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
            // ---------------------------------------------------------
            // Extract and parse payload
            // ---------------------------------------------------------
            //String json = intent.getStringExtra("payload");
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
                int notifCode = ((Number) payload.get("notification_request_code")).intValue();
                if (notifCode != 0) {
                    long candleTime = payload.getLong("next_candle_time");
                    UserSettings.log("AlarmReceiver::onReceive: 5‑min alarm, candle time " + UserSettings.getLogTime(candleTime));

                    // Cancel early notification
                    cancelNotification(context, notifCode);

                    showFinal(context, payload);
                }
            }
        } catch (Exception e) {
            UserSettings.log("AlarmReceiver::onReceive: exception " + e);
        }
    }
    protected void showEarly(Context context, JSONObject payload) throws JSONException {
        int requestCode = payload.getInt("request_code");
        long candleTime = payload.getLong("next_candle_time");
        long notifTime  = payload.getLong("3hour_notif_time");

        // Create channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel early = new NotificationChannel(
                    channel()+ "_early", //"shabbat_early",
                    channel() + "_reminder",
                    NotificationManager.IMPORTANCE_DEFAULT   // must be DEFAULT or higher for sound
            );

            early.setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build()
            );

            early.enableVibration(false);

            nm.createNotificationChannel(early);
        }

        UserSettings.log("AlarmReceiver::showEarly: notification time " + UserSettings.getLogTime(notifTime));

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, "shabbat_early")
                        .setSmallIcon(icon())
                        .setContentTitle("Candle Lighting Reminder")
                        .setContentText("Shabbat candle lighting is at " + UserSettings.getTimestamp(candleTime))
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setAutoCancel(true);

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        nm.notify(requestCode, builder.build());
    }
    protected void showFinal(Context context, JSONObject payload) throws JSONException {
        int requestCode = payload.getInt("request_code");
        long candleTime = payload.getLong("next_candle_time");
        long alarmTime = payload.getLong("5min_alarm_time");

        // Cancel early notification
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(requestCode);

        JSONObject json = new JSONObject();
        json.put("candle_time", UserSettings.getLogTime(candleTime));
        json.put("event", "shabbat");
        json.put("request_code", AlarmUtils.REQ_5MIN);

        prefs.edit().putLong("last_processed_shabbat", candleTime).apply();

        // Start alarm service
        Intent svc = new Intent(context, ShabbatAlarmService.class);
        svc.setAction("ALARM");
        svc.putExtra("payload", json.toString());

        try {
            UserSettings.log("ShabbatAlarmReceiver::showFinal: alarm time " + UserSettings.getLogTime(alarmTime));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        }
        catch (Exception e)
        {
            UserSettings.log("ShabbatAlarmReceiver::showFinal: exception " + e);
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
