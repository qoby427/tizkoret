package com.emhillstudio.tizcoret;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONException;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {

            // 1. Restore all Yahrzeit alarms
            AlarmService.rescheduleAllYahrzeitAlarms(context);

            // 2. Restore Shabbat alarm
            String json = UserSettings.getLastShabbatJson(context);
            if (json != null) {
                try {
                    new ShabbatAlarmReceiver().scheduleNextAlarm(context, json);
                }
                catch (JSONException ex) {
                    System.out.println("BootReceiver::onReceive: " + ex);
                }
            }
        }
    }
}

