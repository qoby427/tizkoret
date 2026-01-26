package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ShabbatAlarmReceiver extends AlarmReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {

        String json = intent.getStringExtra("payload");
        if (json == null)
            return;

        // Start service
        Intent serviceIntent = new Intent(context, AlarmService.class);
        serviceIntent.putExtra("payload", json);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        }

        // Parse existing payload
        Map<String, Object> payload = new Gson().fromJson(json, Map.class);

        // Compute next week's trigger
        long nextWeekTrigger = HebrewUtils.computeNextCandleLighting(context) - 5*60*1000;

        // Update only the changed fields
        payload.put("next_candle_time", nextWeekTrigger);
        // Serialize back
        String nextJson = new Gson().toJson(payload);
        UserSettings.setLastShabbatJson(context, nextJson);
        scheduleNextAlarm(context, nextJson);
    }
}
