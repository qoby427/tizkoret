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
        SharedPreferences prefs = ctx.getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);

        try {
            String json = prefs.getString(intent.getAction(), null);

            Intent svc = new Intent(ctx, LocationService.class);
            svc.putExtra("event_info", json);
            ContextCompat.startForegroundService(ctx, svc);

            //new EventManager(ctx).scheduleIfNeeded(json);
            prefs.edit().remove(intent.getAction()).apply();
        } catch (Exception e) {
            System.out.println("MasterReceiver::onReceive: " + e);
        }
    }
}
