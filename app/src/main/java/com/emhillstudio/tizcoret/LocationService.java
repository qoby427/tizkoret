package com.emhillstudio.tizcoret;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

public class LocationService extends Service {
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startSilentNotification();

        String json = intent != null ? intent.getStringExtra("event_info") : null;

        new EventManager(LocationService.this).scheduleIfNeeded(json);

        stopForeground(true);
        stopSelf();

        return START_NOT_STICKY;
    }

    private void startSilentNotification() {
        String channelId = "generic_location_channel";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Location Service",
                    NotificationManager.IMPORTANCE_MIN
            );

            channel.setSound(null, null);

            NotificationManager nm = getSystemService(NotificationManager.class);
                nm.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_shabbat_candles)
                .setContentTitle("Updating location…")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setSilent(true)
                .build();

        startForeground(1, notification);
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
