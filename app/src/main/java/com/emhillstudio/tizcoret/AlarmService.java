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

import java.util.List;

public class AlarmService extends Service {

    private MediaPlayer mediaPlayer;
    private String eventType;     // "shabbat" or "yahrzeit"
    private String candleTime;    // only for shabbat

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            // System restart, keep ringing
            return START_STICKY;
        }

        // 1. STOP must be handled first
        if ("STOP_ALARM".equals(intent.getAction())) {
            stopAlarm();
            return START_NOT_STICKY;
        }

        // 2. Parse payload
        String json = intent.getStringExtra("payload");
        if(json == null)
            return START_NOT_STICKY;

        try {
            JSONObject payload = new JSONObject(json);

            eventType = payload.getString("event");
            candleTime = payload.getString("candle_time");
        } catch (JSONException ex) {
            System.out.println("AlarmService::onStartCommand: " + ex);
            return START_NOT_STICKY;
        }

        System.out.println("AlarmService::onStartCommand: candle time " + candleTime);

        // 3. Prepare channel + foreground notification
        createNotificationChannel();
        startForeground(1, buildNotification());

        // 4. Start alarm sound
        startAlarmSound(getAlarmTone(eventType));

        return START_STICKY;
    }

    // -----------------------------
    // STOP LOGIC
    // -----------------------------
    private void stopAlarm() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
            }
        } catch (Exception ignored) {}

        mediaPlayer = null;

        stopForeground(true);
        stopSelf();
    }

    // -----------------------------
    // CHANNEL
    // -----------------------------
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel("alarm_channel", "Alarm Channel",
                            NotificationManager.IMPORTANCE_HIGH);

            channel.setSound(null, null);
            channel.enableVibration(false);

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    // -----------------------------
    // RINGTONE SELECTION
    // -----------------------------
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

    // -----------------------------
    // NOTIFICATION
    // -----------------------------
    private Notification buildNotification() {
        Intent stopIntent = new Intent(this, getClass());
        stopIntent.setAction("STOP_ALARM");

        PendingIntent stopPendingIntent = PendingIntent.getService(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        String title = eventType.equals("yahrzeit")
                ? "Yahrzeit Alarm"
                : "Shabbat Alarm";

        String text = "Tap STOP to silence the alarm";

        if ("shabbat".equals(eventType) && candleTime != null)
            text = "Candle lighting at " + candleTime;

        return new NotificationCompat.Builder(this, "alarm_channel")
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_shabbat_candles)
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
                stopAlarm();
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
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // -----------------------------
    // YAHRZEIT RESCHEDULING
    // -----------------------------
    public static void rescheduleAllYahrzeitAlarms(Context context) {
        List<String> jsonList = UserSettings.loadYahrzeitJsonList(context);
        if (jsonList == null || jsonList.isEmpty()) return;

        YahrzeitAlarmReceiver receiver = new YahrzeitAlarmReceiver();
        for (String json : jsonList) {
            try {
                receiver.scheduleNextAlarm(context, json);
            }
            catch (JSONException ex) {
                System.out.println("AlarmService::rescheduleAllYahrzeitAlarms: " + ex);
            }
        }
    }
}
