package com.emhillstudio.tizcoret;

import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class EarlyService extends Service {

    private MediaPlayer mp;
    private String candletime;
    private String event;
    private static final Set<Integer> processing =
            Collections.synchronizedSet(new HashSet<>());
    private final BroadcastReceiver stopReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            UserSettings.log("EarlyService: received STOP_EARLY_ALARM");
            cleanupAndStop();
        }
    };

    @RequiresApi(api = Build.VERSION_CODES.TIRAMISU)
    @Override
    public void onCreate() {
        super.onCreate();
        IntentFilter f = new IntentFilter("STOP_EARLY_ALARM");
        registerReceiver(stopReceiver, f, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        int reqcode = intent.getIntExtra("request_code", 1);

        if (processing.contains(reqcode)) {
            UserSettings.log("EarlyService: duplicate start ignored reqcode=" + reqcode);
            return START_NOT_STICKY;
        }
        processing.add(reqcode);

        candletime = intent.getStringExtra("candle_time");
        event = intent.getStringExtra("event");

        boolean loud = UserSettings.isEarlyLoud(this);
        if (!loud) {
            // QUIET MODE
            showQuietNotification();
            return START_NOT_STICKY;
        }

        // Start as foreground (required for alarm audio)
        startForeground(1, buildForegroundNotification());

        Uri soundUri = UserSettings.getEarlyTone(this);
        playOnceThenDowngrade(soundUri);

        return START_NOT_STICKY;
    }
    private int getIcon() {
        if(event.equals("Shabbat"))
            return R.drawable.ic_shabbat_candles;
        else
            return R.drawable.ic_yahrzeit_candle;
    }
    private Notification buildForegroundNotification() {
        return new NotificationCompat.Builder(this, "early_channel")
                .setSmallIcon(getIcon())
                .setContentTitle(event + " Reminder")
                .setContentText("Candle lighting at " + candletime)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setOngoing(true)          // required for foreground
                .setOnlyAlertOnce(true)
                .build();
    }

    private Notification buildSwipeableNotification() {
        return new NotificationCompat.Builder(this, "early_channel")
                .setSmallIcon(getIcon())
                .setContentTitle(event + " Reminder")
                .setContentText("Candle lighting at " + candletime)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(false)         // <-- user can swipe now
                .setOnlyAlertOnce(true)
                .build();
    }

    /**
     * Plays the sound once, then downgrades the foreground notification
     * into a normal swipeable notification while keeping the service alive.
     */
    private void playOnceThenDowngrade(Uri soundUri) {
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            mp = new MediaPlayer();
            mp.setAudioAttributes(attrs);
            mp.setDataSource(this, soundUri);
            mp.setVolume(1.0f, 1.0f);
            mp.setLooping(false);

            mp.setOnCompletionListener(player -> {
                // 1. Stop foreground mode but KEEP the notification
                stopForeground(false);

                // 2. Replace with a swipeable notification
                Notification swipeable = buildSwipeableNotification();
                if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                    NotificationManagerCompat.from(this).notify(1, swipeable);
                }

                // 3. Release audio resources
                try {
                    player.release();
                } catch (Exception ignored) {}
            });

            mp.prepare();
            mp.start();

        } catch (Exception e) {
            UserSettings.log("EarlyService::playOnceThenDowngrade exception " + e);
        }
    }
    private void showQuietNotification() {
        Notification n = new NotificationCompat.Builder(this, "early_channel")
                .setSmallIcon(R.drawable.ic_shabbat_candles)
                .setContentTitle(event + " Reminder")
                .setContentText("Candle lighting at " + candletime)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(false) // swipeable
                .setOnlyAlertOnce(true)
                .build();

        if (NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            NotificationManagerCompat.from(this).notify(1, n);
        } else {
            UserSettings.log("EarlyService: notifications disabled, cannot post quiet notification");
        }
    }

    public static void clearCode(int reqcode) {
        processing.remove(reqcode);
    }

    private void cleanupAndStop() {
        try {
            if (mp != null) {
                mp.stop();
                mp.release();
                mp = null;
            }
        } catch (Exception ignored) {}

        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(stopReceiver);
        } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
