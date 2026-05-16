package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;

public class MasterReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        try {
            UserSettings.log("MasterReceiver::onReceive fired at " + UserSettings.getLogTime(System.currentTimeMillis()));
        } catch (Exception e) {
            UserSettings.log("MasterReceiver::onReceive write to log failed at " +
                UserSettings.getLogTime(System.currentTimeMillis()) +": " + e);
        }

        SharedPreferences prefs = ctx.getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);
        String json = prefs.getString(intent.getAction(), null);

        try {
            EventManager.getInstance().scheduleIfNeeded(json);
        } catch (Exception e) {
            UserSettings.log("MasterReceiver::onReceive - "+e.toString());
        }

        prefs.edit().remove(intent.getAction()).apply();
    }
}

