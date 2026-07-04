package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UserSettings {
    public static final String PREFS = "prefs";
    public static final String KEY_LAT = "latitude";
    public static final String KEY_LNG = "longitude";
    public static final String KEY_TIME = "time";

    private static final String KEY_SHABBAT_ALARM = "shabbat_alarm_enabled";
    private static final String KEY_YAHRZEIT_LIST = "yahrzeit_list";
    public static final String SHABBAT_RINGTONE = "shabbat_ringtone";
    public static final String YAHRZEIT_RINGTONE = "yahrzeit_ringtone";

    private enum DateFormat {
        AMERICAN,
        EUROPEAN,
    }
    private static DateFormat dateFormat = DateFormat.AMERICAN;

    // -----------------------------
    //  Coordinates
    // -----------------------------
    public static void setLatitude(Context ctx, double lat) {
        prefs(ctx).edit().putFloat(KEY_LAT, (float) lat).apply();
    }

    public static void setLongitude(Context ctx, double lng) {
        prefs(ctx).edit().putFloat(KEY_LNG, (float) lng).apply();
    }
    public static void setLocationTime(Context ctx, long time) {
        prefs(ctx).edit().putLong(KEY_TIME, time).apply();
    }
    public static double getLatitude(Context ctx) {
        return prefs(ctx).getFloat(KEY_LAT, 0f);
    }

    public static double getLongitude(Context ctx) {
        return prefs(ctx).getFloat(KEY_LNG, 0f);
    }
    public static long getLocationTime(Context ctx) {
        return prefs(ctx).getLong(KEY_TIME, 0);
    }

    // -----------------------------
    //  Shabbat Alarm Toggle
    // -----------------------------
    public static void setShabbatAlarmEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_SHABBAT_ALARM, enabled).apply();
    }

    public static boolean isShabbatAlarmEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SHABBAT_ALARM, false);
    }

    public static void clearEvents(Context ctx) {
        prefs(ctx).edit().putLong("processed_shabbat_time", 0).apply();
        prefs(ctx).edit().putLong("debug_last_candle", 0).apply();

        List<YahrzeitEntry> list = loadYahrzeitList(ctx);
        for (YahrzeitEntry entry : list)
            prefs(ctx).edit().putLong("processed_yahrzeit_" + entry.name, 0).apply();
    }

    // -----------------------------
    //  Yahrzeit List (JSON array)
    // -----------------------------
    @SuppressLint("ObsoleteSdkInt")
    public static void saveYahrzeitList(Context context, List<YahrzeitEntry> list) {
        list.removeIf(e ->
                e.name == null || e.name.trim().isEmpty() || e.diedDate == null
        );

        Gson gson = new Gson();
        String json = gson.toJson(list);

        prefs(context).edit().putString(KEY_YAHRZEIT_LIST, json).apply();
        saveYahrzeitList(context, json);
    }
    private static void saveYahrzeitList(Context context, String json) {
        File file = getPersistentData(context);

        try (FileWriter writer = new FileWriter(file)) {
            writer.write(json);
            UserSettings.log("saveYahrzeitList: yahrzeits saved");
        } catch (Exception e) {
            UserSettings.log("saveYahrzeitList: Failed to save yahrzeit file: " + e);
        }
    }
    public static List<YahrzeitEntry> loadYahrzeitList(Context context) {
        String json = prefs(context).getString(KEY_YAHRZEIT_LIST, "");
        if (json.isEmpty()) {
            try {
                File file = getPersistentData(context);
                json = new String(Files.readAllBytes(file.toPath()));
            } catch (Exception e) {
                //UserSettings.log("getYahrzeitList: file does not exist");
            }
        }
        if (json.isEmpty())
            return new ArrayList<>();

        Gson gson = new Gson();
        Type type = new TypeToken<List<YahrzeitEntry>>() { }.getType();
        List<YahrzeitEntry> list = gson.fromJson(json, type);
        list.removeIf(e ->
                e.name == null || e.name.trim().isEmpty() || e.diedDate == null
        );
        return list;
    }
    public static Uri getShabbatRingtone(Context context) {
        //prefs(context).edit().remove(SHABBAT_RINGTONE).apply();
        //prefs(context).edit().remove(YAHRZEIT_RINGTONE).apply();
        String uriString = prefs(context).getString(SHABBAT_RINGTONE, null);

        if (uriString != null && !uriString.trim().isEmpty()) {
            return Uri.parse(uriString);
        }

        Uri uri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.lecha_dodi);
        if(uri != null)
            return uri;
        // Fallback to system default alarm sound
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    }
    public static Uri getYahrzeitRingtone(Context context) {
        String uriString = prefs(context).getString(YAHRZEIT_RINGTONE, null);

        if (uriString != null && !uriString.trim().isEmpty()) {
            return Uri.parse(uriString);
        }

        Uri uri = Uri.parse("android.resource://" + context.getPackageName() + "/" + R.raw.schumann_fantasy);
        if(uri != null)
            return uri;
        // Fallback to system default alarm sound
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    }
    private static File getPersistentData(Context context) {
        File base = new File(
                Environment.getExternalStorageDirectory(),
                "Android/media/" + context.getPackageName() + "/data"
        );
        if (!base.exists() && !base.mkdirs()) {
            UserSettings.log("Failed to create persistent data directory: " + base);
        }
        return new File(base, "yahrzeits.json");
    }
    public static void setYahrzeitRingtone(Context context, Uri uri) {
        prefs(context).edit().putString(YAHRZEIT_RINGTONE, uri.toString()).apply();
    }
    public static void setShabbatRingtone(Context context, Uri uri) {
        prefs(context).edit().putString(SHABBAT_RINGTONE, uri.toString()).apply();
    }
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, MODE_PRIVATE);
    }
    public static boolean isEarlyLoud(Context ctx) {
        return ctx.getSharedPreferences(PREFS, MODE_PRIVATE)
                .getBoolean("early_loud", true); // Loud = default
    }
    public static void setEarlyLoud(Context ctx, boolean loud) {
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit()
                .putBoolean("early_loud", loud)
                .apply();
    }
    public static Uri getEarlyTone(Context ctx) {
        String uriString = ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getString("early_tone_uri", null);
        if (uriString != null) {
            try {
                return Uri.parse(uriString);
            } catch (Exception ignored) {}
        }
        return Uri.parse("android.resource://" + ctx.getPackageName() + "/" + R.raw.notif18min_loud);
    }

    public static String getDateTime(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat(getDateFormat() + " yyyy h:mm a", Locale.getDefault());
        return sdf.format(new Date(millis));
    }
    public static String getTimestamp(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat(getTimeFormat(), Locale.getDefault());
        return sdf.format(new Date(millis));
    }
    public static String getLogTime(long millis) {
        return getDateTime(millis);
    }
    public static void setDateFormat(boolean american) {
        dateFormat = american ? DateFormat.AMERICAN : DateFormat.EUROPEAN;
    }
    private static String getDateFormat() {
        return dateFormat == DateFormat.AMERICAN ? "MMM d": "d MMM";
    }
    private static String getTimeFormat() {
        return dateFormat == DateFormat.AMERICAN ? "h:mm a": "HH:mm";
    }

    public static void log(String msg) {
        Log.d("Tizcoret Debug", msg);
        LogManager.log(msg);
    }
    public static boolean isDebug() { return false && BuildConfig.DEBUG; }

    public static void clearLog() {
        LogManager.clearLog();
    }
}
