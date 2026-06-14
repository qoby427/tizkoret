package com.emhillstudio.tizcoret;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AlarmService extends Service {
    private MediaPlayer mediaPlayer;
    private static final Set<Integer> processing =  Collections.synchronizedSet(new HashSet<>());
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (intent == null || intent.getAction() == null) {
            // System restart, keep ringing
            return START_STICKY;
        }

        if ("ALARM".equals(intent.getAction())) {

            String event = intent.getStringExtra("event");
            String message = intent.getStringExtra("message");
            int reqcode = intent.getIntExtra("request_code", 1);

            UserSettings.log("AlarmService::onStartCommand event=" + event + " reqcode=" + reqcode);

            // ⭐ Duplicate suppression
            if (processing.contains(reqcode)) {
                UserSettings.log("Duplicate start ignored for reqcode=" + reqcode);
                return START_NOT_STICKY;
            }
            processing.add(reqcode);

            // ⭐ Build + start foreground notification
            Notification notif = buildNotification(event, message);
            startForeground(reqcode, notif);

            // ⭐ Delegate to child class
            handleAlarm(intent, reqcode);
        }

        return START_STICKY;
    }
    // ⭐ Child classes override this to run alarm logic + scheduling
    protected void handleAlarm(Intent intent, int reqcode) {
        try {
            // 1. Start alarm sound
            startAlarmSound(getAlarmTone("Shabbat"));

            // 2. Schedule next week's Shabbat alarm
            EventManager.init(this);
            EventManager.getInstance().scheduleIfNeeded();

        } finally {
            // 3. Remove reqcode + stop service
            finishAlarm(reqcode);
        }
    }
    // ⭐ Call this when alarm logic is fully done
    protected void finishAlarm(int reqcode) {
        processing.remove(reqcode);
        stopSelf();
    }

    // -----------------------------
    // NOTIFICATION
    // -----------------------------
    private Notification buildNotification(String event, String message) {

        Intent stopIntent = new Intent(this, StopAllReceiver.class);

        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String channelId;
        int icon;

        if ("Shabbat".equals(event)) {
            channelId = "shabbat_channel";
            icon = R.drawable.ic_shabbat_candles;
        } else {
            channelId = "yahrzeit_channel";
            icon = R.drawable.ic_yahrzeit_candle;
        }

        return new NotificationCompat.Builder(this, channelId)
                .setContentTitle(event + " Alarm")
                .setContentText(message)
                .setSmallIcon(icon)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "STOP", stopPendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build();
    }

    // -----------------------------
    // SOUND ENGINE
    // -----------------------------
    protected void startAlarmSound(Uri alarmUri) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            fadeInVolume();

        } catch (Exception e) {
            UserSettings.log("AlarmService::startAlarmSound error: " + e);
        }
    }

    private void fadeInVolume() {
        Handler handler = new Handler(Looper.getMainLooper());
        final float[] volume = {0f};

        handler.post(new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer == null) return;

                volume[0] += 0.05f;
                if (volume[0] <= 1f) {
                    mediaPlayer.setVolume(volume[0], volume[0]);
                    handler.postDelayed(this, 500);
                }
            }
        });
    }

    protected Uri getAlarmTone(String event) {
        Uri saved = event.equals("Shabbat")
                ? UserSettings.getShabbatRingtone(this)
                : UserSettings.getYahrzeitRingtone(this);

        if (saved != null) return saved;

        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri != null) return uri;

        uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION);
        if (uri != null) return uri;

        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    }

    // -----------------------------
    // CLEANUP
    // -----------------------------
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAlarm();   // ⭐ ONLY cleanup here
    }

    private void stopAlarm() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }
        } catch (Exception e) {
            UserSettings.log("AlarmService::stopAlarm: " + e);
        }

        mediaPlayer = null;
        stopForeground(true);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}