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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AlarmService extends Service {

    private MediaPlayer mediaPlayer;
    private String eventType; // "shabbat" or "yahrzeit"

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        // Determine which alarm type this is
        eventType = intent.getStringExtra("event");
        if (eventType == null) eventType = "shabbat"; // fallback

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

        } catch (Exception e) {
            e.printStackTrace();
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
        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String saved = prefs.getString("ringtone_" + event, null);

        if (saved != null) return Uri.parse(saved);

        Uri uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM);
        if (uri != null) return uri;

        uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_NOTIFICATION);
        if (uri != null) return uri;

        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
    }

    private Notification createNotification() {

        Intent stopIntent = new Intent(this, StopAlarmReceiver.class);
        stopIntent.setAction("STOP_ALARM");
        stopIntent.putExtra("event", eventType);

        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
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

        return new NotificationCompat.Builder(this, "alarm_channel")
                .setContentTitle(title)
                .setContentText("Tap STOP to silence the alarm")
                .setSmallIcon(R.drawable.ic_alarm)
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
        SharedPreferences prefs = context.getSharedPreferences("prefs", MODE_PRIVATE);
        List<YahrzeitEntry> entries = UserSettings.loadYahrzeitList(context);
        String uriString = prefs.getString("ringtone_alarm", null);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd", Locale.US);

        if (entries == null || entries.isEmpty()) {
            return;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        for (YahrzeitEntry entry : entries) {
            Date diedDate = entry.diedDate;
            String ringtone = prefs.getString("ringtone_alarm", sdf.format(diedDate));

            // 2. Compute next Yahrzeit date
            Date nextDate = HebrewUtils.computeInYearDate(diedDate,1);
            long triggerAtMillis = nextDate.getTime();

            // 3. Build the PendingIntent
            Intent intent = new Intent(context, YahrzeitAlarmReceiver.class);
            intent.putExtra("event", "yahrzeit");
            intent.putExtra("died_date", diedDate);
            intent.putExtra("ringtone_uri", ringtone);

            // Unique requestCode per entry
            int requestCode = diedDate.hashCode();

            PendingIntent pi = PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // 4. Schedule the alarm
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pi
                    );
                } else {
                    am.setExact(
                            AlarmManager.RTC_WAKEUP,
                            triggerAtMillis,
                            pi
                    );
                }
            } catch (SecurityException ignored) {
                // Exact alarms disabled
            }
        }
    }
}
