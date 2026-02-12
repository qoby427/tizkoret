package com.emhillstudio.tizcoret;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.os.Looper;
import android.os.Handler;
import android.content.SharedPreferences;
import android.app.Notification;

import androidx.core.app.NotificationCompat;

public class YahrzeitAlarmService extends Service {

    private MediaPlayer mediaPlayer;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "yahrzeit_alarm_channel",
                    "Yahrzeit Alarm Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }

        // MUST be first!
        startForeground(1, createNotification());

        Uri alarmUri = getAlarmTone();

        try {
            mediaPlayer = new MediaPlayer();
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setDataSource(this, alarmUri);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
            mediaPlayer.start();

            fadeInVolume();
            autoStopAfterOneMinute();

        } catch (Exception e) {
            e.printStackTrace();
        }

        return START_STICKY;
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
    private Uri getAlarmTone() {
        SharedPreferences prefs = getSharedPreferences("settings", MODE_PRIVATE);
        String saved = prefs.getString("ringtone_yahrzeit", null);

        if (saved != null) {
            return Uri.parse(saved);
        }

        // User has not selected a ringtone → use system default alarm
        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(
                this,
                RingtoneManager.TYPE_ALARM
        );

        if (uri != null) return uri;

        // Fallback: default notification tone
        uri = RingtoneManager.getActualDefaultRingtoneUri(
                this,
                RingtoneManager.TYPE_NOTIFICATION
        );

        if (uri != null) return uri;

        // Last fallback: any available ringtone
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    }
    private Notification createNotification() {

        Intent stopIntent = new Intent(this, StopAlarmReceiver.class);
        stopIntent.setAction("STOP_ALARM");

        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Action stopAction =
                new NotificationCompat.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        "STOP",
                        stopPendingIntent
                ).build();

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, "alarm_channel")
                        .setContentTitle("Alarm is ringing")
                        .setContentText("Tap STOP to silence the alarm")
                        .setSmallIcon(R.drawable.ic_alarm)
                        .setOngoing(true)
                        .addAction(stopAction)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setCategory(NotificationCompat.CATEGORY_ALARM);

        return builder.build();
    }
}
