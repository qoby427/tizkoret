package com.emhillstudio.tizcoret;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class DailyWorker extends Worker {
    private Context ctx;
    private SharedPreferences prefs;
    private EventManager manager;
    private final SimpleDateFormat DATE_ONLY = new SimpleDateFormat("yyyyMMdd", Locale.US);

    public DailyWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        ctx = context;
        prefs = context.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public Result doWork() {
        Calendar cal = Calendar.getInstance();
        int dayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
        boolean isFriday = dayOfWeek == Calendar.FRIDAY;
        List<YahrzeitEntry> todaysYahrzeits = UserSettings.loadYahrzeitList(ctx);

        if (isFriday) {
            manager = new EventManager(ctx);
            manager.scheduleIfNeeded();
        }
        else {
            String yz = "";
            for (YahrzeitEntry entry : todaysYahrzeits) {
                String entryStr = DATE_ONLY.format(entry.inYear);
                String todayStr = DATE_ONLY.format(Calendar.getInstance().getTime());

                if (entryStr == todayStr) {
                    yz += (yz.isEmpty() ? "" : ", ") + entry.name;
                }
            }

            if (!yz.isEmpty())
                yz = "Yahrzeit of " + yz + " is tomorrow";

            if (yz.isEmpty()) {
                UserSettings.log("DailyWorker: no yahrzeit today");
            }
            else {
                prefs.edit().putString("yahrzeit_message", yz).apply();
                UserSettings.log("ShabbatDailyWorker executed at " +
                        UserSettings.getLogTime(System.currentTimeMillis()) + " in Debug = " + UserSettings.isDebug());
                //TODO: manager = new YahrzeitSchedulerManager(ctx);
                manager.scheduleIfNeeded();
            }
        }

        return Result.success();
    }
}
