package com.emhillstudio.tizcoret;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import java.util.Map;

public class ShabbatAlarmReceiver extends AlarmReceiver {
    @Override
    protected void showEarly(Context context, Map<String, Object> payload) {

        int requestCode = ((Number) payload.get("request_code")).intValue();

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

            nm.createNotificationChannel(early);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, "shabbat_early")
                        .setSmallIcon(R.drawable.ic_shabbat_candles)
                        .setContentTitle("Candle Lighting Reminder")
                        .setContentText("Candle lighting is in 3 hours")
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setAutoCancel(true)
                        .setSound(null);  // SILENT

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        nm.notify(requestCode, builder.build());
    }
    @Override
    protected void showFinal(Context context, Map<String, Object> payload) {

        int requestCode = ((Number) payload.get("request_code")).intValue();

        // Create channel if needed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel fin = new NotificationChannel(
                    "shabbat_final",
                    "Shabbat Alarm",
                    NotificationManager.IMPORTANCE_HIGH
            );

            Uri sound = UserSettings.getShabbatRingtone(context);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();
            fin.setSound(sound, attrs);

            nm.createNotificationChannel(fin);
        }

        Uri sound = UserSettings.getShabbatRingtone(context);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, "shabbat_final")
                        .setSmallIcon(R.drawable.ic_shabbat_candles)
                        .setContentTitle("Candle Lighting")
                        .setContentText("Time to light candles")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setSound(sound);

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        nm.notify(requestCode, builder.build());
    }
}

