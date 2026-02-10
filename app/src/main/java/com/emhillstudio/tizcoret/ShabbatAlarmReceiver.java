package com.emhillstudio.tizcoret;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class ShabbatAlarmReceiver extends AlarmReceiver {
    @Override
    protected void showEarly(Context context, JSONObject payload) throws JSONException {
        int requestCode = payload.getInt("request_code");
        long candleTime = payload.getLong("next_candle_time");
        long notifTime = payload.getLong("3hour_notif_time");

        // Create channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel early = new NotificationChannel(
                    "shabbat_early",
                    "Shabbat Early Reminder",
                    NotificationManager.IMPORTANCE_LOW
            );

            early.setSound(null, null);   // SILENT
            early.enableVibration(false);

            nm.createNotificationChannel(early);
        }

        System.out.println("ShabbatAlarmReceiver::showEarly: notification time " + UserSettings.getTimestamp(notifTime));

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, "shabbat_early")
                        .setSmallIcon(R.drawable.ic_shabbat_candles)
                        .setContentTitle("Candle Lighting Reminder")
                        .setContentText("Candle lighting is at " + UserSettings.getTimestamp(candleTime))
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setAutoCancel(true)
                        .setSound(null);  // SILENT

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        nm.notify(requestCode, builder.build());
    }
    @Override
    protected void showFinal(Context context, JSONObject payload) throws JSONException {
        int requestCode = payload.getInt("request_code");
        long candleTime = payload.getLong("next_candle_time");
        long alarmTime = payload.getLong("5min_alarm_time");

        // Cancel early notification
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(requestCode);

        JSONObject json = new JSONObject();
        json.put("candle_time", UserSettings.getTimestamp(candleTime));
        json.put("event", "shabbat");
        json.put("request_code", AlarmUtils.REQ_5MIN);

        // Start alarm service
        Intent svc = new Intent(context, ShabbatAlarmService.class);
        svc.setAction("ALARM");
        svc.putExtra("payload", json.toString());

        try {
            System.out.println("ShabbatAlarmReceiver::showFinal: alarm time " + UserSettings.getTimestamp(alarmTime));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        }
        catch (Exception e)
        {
            System.out.println("ShabbatAlarmReceiver::showFinal: exception " + e);
        }
    }
}

