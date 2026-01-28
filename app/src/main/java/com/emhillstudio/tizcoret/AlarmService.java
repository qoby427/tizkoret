package com.emhillstudio.tizcoret;

import android.app.AlarmManager;
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
import android.net.ParseException;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AlarmService extends Service {

    private MediaPlayer mediaPlayer;
    private String eventType; // "shabbat" or "yahrzeit"
    private String candleTime;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // 1. Handle STOP action first
        if (intent != null && "STOP_ALARM".equals(intent.getAction())) {
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.stop();
                    mediaPlayer.release();
                } catch (Exception ignored) {}
                mediaPlayer = null;
            }

            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }

        // 2. Normal alarm start
        String json = intent.getStringExtra("payload");
        Map<String, Object> payload = new Gson().fromJson(json, Map.class);
        eventType = (String) payload.get("event");
        candleTime = (String) payload.get("candle_time");

        createNotificationChannel();
        startForeground(1, createNotification());

        Uri alarmUri = getAlarmTone(eventType);

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            fadeInVolume();
            autoStopAfterOneMinute();

        }
        catch (Exception e) {
        }

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "alarm_channel",
                    "Alarm Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private Uri getAlarmTone(String event) {
        SharedPreferences prefs = getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);
        String saved = prefs.getString("ringtone_" + event, null);

        if (saved != null) return Uri.parse(saved);

        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri != null) return uri;

        uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION);
        if (uri != null) return uri;

        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    }

    private Notification createNotification() {

        // STOP goes directly to AlarmService
        Intent stopIntent = new Intent(this, AlarmService.class);
        stopIntent.setAction("STOP_ALARM");
        stopIntent.putExtra("event", eventType);

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                2001,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = eventType.equals("yahrzeit")
                ? "Yahrzeit Alarm"
                : "Shabbat Alarm";

        NotificationCompat.Action stopAction =
                new NotificationCompat.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        "STOP",
                        stopPendingIntent
                ).build();

        String text = "Tap STOP to silence the alarm";

        if ("shabbat".equals(eventType) && candleTime != null)
            text = "Candle lighting at " + candleTime + " — " +text;

        return new NotificationCompat.Builder(this, "alarm_channel")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_shabbat_candles)
                .setOngoing(true)
                .addAction(stopAction)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build();
    }

    private void fadeInVolume() {
        Handler handler = new Handler(Looper.getMainLooper());
        final float[] volume = {0f};
        final float maxVolume = 1f;

        Runnable ramp = new Runnable() {
            @Override
            public void run() {
                volume[0] += 0.05f;
                if (mediaPlayer != null && volume[0] <= maxVolume) {
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
                mediaPlayer.stop();
            }
        }, 60_000);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    public static void rescheduleAllYahrzeitAlarms(Context context) {
        List<String> jsonList = UserSettings.loadYahrzeitJsonList(context);
        if (jsonList == null || jsonList.isEmpty()) return;

        YahrzeitAlarmReceiver receiver = new YahrzeitAlarmReceiver();

        for (String json : jsonList) {
            receiver.scheduleNextAlarm(context, json);
        }
    }
}
