package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class MasterReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        SharedPreferences prefs = ctx.getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);

        try {
            String json = prefs.getString(intent.getAction(), null);

            new EventManager(ctx).scheduleIfNeeded(json);
            prefs.edit().remove(intent.getAction()).apply();
        } catch (Exception e) {
            System.out.println("MasterReceiver::onReceive: " + e);
        }
    }
}
