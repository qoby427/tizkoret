package com.emhillstudio.tizcoret;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import java.util.Map;

public class YahrzeitAlarmReceiver extends AlarmReceiver {
    @Override
    protected void showEarly(Context context, Map<String, Object> payload) {

        int requestCode = ((Number) payload.get("request_code")).intValue();

        // Ensure channels exist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel early = new NotificationChannel(
                    "yahrzeit_early",
                    "Yahrzeit Early Reminder",
                    NotificationManager.IMPORTANCE_LOW
            );
            early.setSound(null, null);   // SILENT

            nm.createNotificationChannel(early);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, "yahrzeit_early")
                        .setSmallIcon(R.drawable.ic_yahrzeit_candle)   // your icon
                        .setContentTitle("Yahrzeit Reminder")
                        .setContentText("The Yahrzeit is in 3 hours")
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .setAutoCancel(true)
                        .setSound(null);                        // SILENT

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        nm.notify(requestCode, builder.build());
    }
    @Override
    protected void showFinal(Context context, Map<String, Object> payload) {

        int requestCode = ((Number) payload.get("request_code")).intValue();

        Uri sound = UserSettings.getYahrzeitRingtone(context);

        // Ensure channels exist
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager)
                    context.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationChannel fin = new NotificationChannel(
                    "yahrzeit_final",
                    "Yahrzeit Alarm",
                    NotificationManager.IMPORTANCE_HIGH
            );

            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .build();
            fin.setSound(sound, attrs);

            nm.createNotificationChannel(fin);
        }

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(context, "yahrzeit_final")
                        .setSmallIcon(R.drawable.ic_yahrzeit_candle)
                        .setContentTitle("Yahrzeit")
                        .setContentText("The Yahrzeit time has arrived")
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .setSound(sound);

        NotificationManager nm = (NotificationManager)
                context.getSystemService(Context.NOTIFICATION_SERVICE);

        nm.notify(requestCode, builder.build());
    }
}
