package com.emhillstudio.tizcoret;

import android.annotation.SuppressLint;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.location.Location;
import android.os.Build;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class App extends Application {
    @SuppressLint("MissingPermission")
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();

        LogManager.init(this);
        try {
            EventManager.init(this);
        } catch (Exception e) {
            UserSettings.log("App::onCreate - " + e);
        }
        // Modern, valid passive request (2024–2026)
        LocationRequest req = new LocationRequest.Builder(Priority.PRIORITY_PASSIVE)
                .setMinUpdateIntervalMillis(0)
                .setMinUpdateDistanceMeters(0)
                .setMaxUpdateDelayMillis(5000)   // REQUIRED for passive mode to work
                .build();

        LocationCallback cb = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult r) {
                if (r != null) {
                    Location loc = r.getLastLocation();
                    if(loc != null) {
                        PassiveLocationStore.update(App.this, loc);
                        return;
                    }
                }
                UserSettings.log("App::onLocationResult - no location provided");
            }
        };

        FusedLocationProviderClient fused =
                LocationServices.getFusedLocationProviderClient(this);

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


