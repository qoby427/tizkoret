package com.emhillstudio.tizcoret;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

public class AlarmService extends Service {

    private MediaPlayer mediaPlayer;
    private String  event;
    private String message;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);

        if (intent == null || intent.getAction() == null) {
            // System restart, keep ringing
            return START_STICKY;
        }

        // 2. Parse payload
        if("ALARM".equals(intent.getAction())) {
            message = intent.getStringExtra("message");
            event = intent.getStringExtra("event");

            String candleTime = intent.getStringExtra("candle_time");
            int reqcode = intent.getIntExtra("request_code", 1);

            UserSettings.log("AlarmService::onStartCommand: event=" + event + " reqcode=" + reqcode + " candle time " + candleTime);

            startForeground(reqcode, buildNotification());
            startAlarmSound(getAlarmTone(event));
        }
        return START_STICKY;
    }

    private void stopAlarm() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }
        } catch (Exception ignored) {}

        mediaPlayer = null;

        stopForeground(true);
    }
    // -----------------------------
    // RINGTONE SELECTION
    // -----------------------------
    private Uri getAlarmTone(String event) {
        SharedPreferences prefs = getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);
        Uri saved = event.equals("Shabbat") ?
                UserSettings.getShabbatRingtone(this) :
                UserSettings.getYahrzeitRingtone(this);

        if (saved != null) return saved;

        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri != null) return uri;

        uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION);
        if (uri != null) return uri;

        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    }

    // -----------------------------
    // NOTIFICATION
    // -----------------------------
    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, StopAllReceiver.class);

        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String channelId;
        int icon;
        if(event.equals("Shabbat")) {
            channelId = "shabbat_channel";
            icon = R.drawable.ic_shabbat_candles;
        }
        else {
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
    private void startAlarmSound(Uri alarmUri) {
        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            fadeInVolume();
            autoStopAfterOneMinute();

        } catch (Exception ignored) {}
    }

    private void fadeInVolume() {
        Handler handler = new Handler(Looper.getMainLooper());
        final float[] volume = {0f};

        Runnable ramp = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer == null) return;

                volume[0] += 0.05f;
                if (volume[0] <= 1f) {
                    mediaPlayer.setVolume(volume[0], volume[0]);
                    handler.postDelayed(this, 500);
                }
            }
        };

        handler.post(ramp);
    }

    private void autoStopAfterOneMinute() {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (mediaPlayer != null) {
                stopSelf();
            }
        }, 60_000);
    }

    // -----------------------------
    // CLEANUP
    // -----------------------------
    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAlarm();

        new EventManager(this).scheduleIfNeeded();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
