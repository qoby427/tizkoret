package com.emhillstudio.tizcoret;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.TimeZone;

public class TimezoneReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        UserSettings.log("Action = " + intent.getAction());
        String tz = intent.getStringExtra("time-zone");
        UserSettings.log("New timezone = " + tz);

        if (Intent.ACTION_TIMEZONE_CHANGED.equals(intent.getAction())) {
            UserSettings.log("TimezoneReceiver::Timezone changed to " + TimeZone.getDefault().getID());
            if(UserSettings.isShabbatAlarmEnabled(context))
                EventManager.getInstance().scheduleImmediately();
        }
    }
}