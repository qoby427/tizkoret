package com.emhillstudio.tizcoret;

import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return;

        NotificationManager manager = getSystemService(NotificationManager.class);

        // Shabbat notifications (early + final notification)
        NotificationChannel shabbat = new NotificationChannel(
                "shabbat_channel",
                "Shabbat Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        shabbat.setDescription("Shabbat candle-lighting reminders");
        shabbat.enableVibration(false);

        // Yahrzeit notifications (early + final notification)
        NotificationChannel yahrzeit = new NotificationChannel(
                "yahrzeit_channel",
                "Yahrzeit Notifications",
                NotificationManager.IMPORTANCE_DEFAULT
        );
        yahrzeit.setDescription("Yahrzeit reminders");
        yahrzeit.enableVibration(false);

        manager.createNotificationChannel(shabbat);
        manager.createNotificationChannel(yahrzeit);
    }
}


