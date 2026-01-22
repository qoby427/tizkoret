package com.emhillstudio.tizcoret;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class YahrzeitAlarmReceiver extends BroadcastReceiver {
    private SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy", Locale.US);

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent serviceIntent = new Intent(context, AlarmService.class);
        serviceIntent.putExtra("event", "yahrzeit");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent);
        }

        String ringtone = intent.getStringExtra("ringtone_yahrzeit");
        String diedDate = intent.getStringExtra("died_date");

        try {
            scheduleNextAlarm(context, ringtone, sdf.parse(diedDate));
        } catch (ParseException e) {
        }
    }
    private void scheduleNextAlarm(Context context, String ringtone, Date date) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        // Check exact alarm permission on Android 12+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !am.canScheduleExactAlarms()) {
            return;
        }

        Intent intent = new Intent(context, YahrzeitAlarmReceiver.class);
        intent.putExtra("ringtone_yahrzeit", ringtone);
        intent.putExtra("died_date", sdf.format(date));

        Date nextDate = HebrewUtils.computeInYearDate(date,1);

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                sdf.format(date).hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextDate.getTime(), pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, nextDate.getTime(), pi);
            }
        } catch (SecurityException e) {
            // Exact alarms disabled
        }
    }
}
