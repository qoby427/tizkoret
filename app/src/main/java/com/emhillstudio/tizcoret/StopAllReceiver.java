// StopAllReceiver.java
package com.emhillstudio.tizcoret;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class StopAllReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        context.stopService(new Intent(context, ShabbatAlarmService.class));
        context.stopService(new Intent(context, YahrzeitAlarmService.class));
    }
}
