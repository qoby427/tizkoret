package com.emhillstudio.tizcoret;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.gson.Gson;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class YahrzeitAlarmReceiver extends AlarmReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        String json = intent.getStringExtra("payload");
        if (json == null) return;

        // Pass JSON to AlarmService
        Intent serviceIntent = new Intent(context, AlarmService.class);
        serviceIntent.putExtra("payload", json);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }

        // Parse payload
        Map<String, Object> payload = new Gson().fromJson(json, Map.class);

        Number diedNum = (Number) payload.get("died_date");
        Date diedDate = new Date(diedNum.longValue());

        // Compute next Hebrew anniversary
        Date nextDate = HebrewUtils.computeInYearDate(diedDate, 1);
        payload.put("next_candle_time", nextDate.getTime());

        String nextJson = new Gson().toJson(payload);
        UserSettings.saveYahrzeitJson(context, json);
        scheduleNextAlarm(context, nextJson);
    }
}
