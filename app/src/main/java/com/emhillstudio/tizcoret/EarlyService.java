package com.emhillstudio.tizcoret;

import android.Manifest;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class EarlyService extends Service {
    private TextToSpeech tts;
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

        UserSettings.log("EarlyService::onStartCommand - starting "+ (loud?"loud":"quiet") + " service");

        if (loud) {
            startForeground(1, buildForegroundNotification());

            playOnceThenDowngrade();
            UserSettings.log("EarlyService::onStartCommand - playing message");
        }
        else {
            showQuietNotification();
        }
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
    private void playOnceThenDowngrade() {
        try {
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            AudioAttributes ttsAttrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            AudioFocusRequest afr = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(ttsAttrs)
                    .setOnAudioFocusChangeListener(focusChange -> {})
                    .setWillPauseWhenDucked(false)
                    .build();

            am.requestAudioFocus(afr);

            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    tts.setLanguage(Locale.US);

                    tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                        @Override
                        public void onDone(String utteranceId) {
                            new Handler(Looper.getMainLooper()).post(() -> {

                                stopForeground(false);

                                Notification swipeable = buildSwipeableNotification();
                                NotificationManagerCompat nm =
                                        NotificationManagerCompat.from(EarlyService.this);

                                if (nm.areNotificationsEnabled() &&
                                        ContextCompat.checkSelfPermission(EarlyService.this,
                                                Manifest.permission.POST_NOTIFICATIONS)
                                                == PackageManager.PERMISSION_GRANTED) {

                                    try {
                                        nm.notify(1, swipeable);
                                    } catch (SecurityException ignored) {}
                                }

                                tts.shutdown();
                                am.abandonAudioFocusRequest(afr);
                            });
                        }

                        @Override public void onStart(String id) {}
                        @Override public void onError(String id) {}
                    });

                    Bundle params = new Bundle();
                    params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "tizcoret_tts");

                    tts.speak("Candlelighting in 18 minutes at "+candletime,
                            TextToSpeech.QUEUE_FLUSH, params, "tizcoret_tts");
                }
            });
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
