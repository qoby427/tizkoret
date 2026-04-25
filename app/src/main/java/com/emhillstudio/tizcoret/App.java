package com.emhillstudio.tizcoret;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Build;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class App extends Application {
    @Override
    @SuppressLint("MissingPermission")
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();

        LogManager.init(this);
        EventManager.init(this);

        LocationRequest req = new LocationRequest.Builder(0)
            .setPriority(Priority.PRIORITY_PASSIVE)
            .setMinUpdateIntervalMillis(0)
            .setMinUpdateDistanceMeters(0)
            .build();

        LocationCallback cb = new LocationCallback() {
            @Override public void onLocationResult(LocationResult r) {
                if (r != null && r.getLastLocation() != null)
                    PassiveLocationStore.update(r.getLastLocation());
            }
        };

        FusedLocationProviderClient fused = LocationServices.getFusedLocationProviderClient(this);
        fused.requestLocationUpdates(req, cb, Looper.getMainLooper());
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


