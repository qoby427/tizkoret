package com.emhillstudio.tizcoret;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            LogManager.init(context);
            EventManager.init(context);
            UserSettings.log("BootReceiver::onReceive - resuming service after reboot");
            EventManager.getInstance().scheduleImmediately();
        }
    }
}

