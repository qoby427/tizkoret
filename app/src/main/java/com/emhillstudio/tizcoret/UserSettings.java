package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class UserSettings {
    private static SharedPreferences logprefs;

    public static final String PREFS = "prefs";
    private static final String KEY_LAT = "latitude";
    private static final String KEY_LNG = "longitude";
    private static final String KEY_SHABBAT_ALARM = "shabbat_alarm_enabled";
    private static final String KEY_YAHRZEIT_LIST = "yahrzeit_list";

    // -----------------------------
    //  Coordinates
    // -----------------------------
    public static void setLatitude(Context ctx, double lat) {
        prefs(ctx).edit().putFloat(KEY_LAT, (float) lat).apply();
    }

    public static void setLongitude(Context ctx, double lng) {
        prefs(ctx).edit().putFloat(KEY_LNG, (float) lng).apply();
    }

    public static double getLatitude(Context ctx) {
        return prefs(ctx).getFloat(KEY_LAT, 0f);
    }

    public static double getLongitude(Context ctx) {
        return prefs(ctx).getFloat(KEY_LNG, 0f);
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
        List<YahrzeitEntry> list = loadYahrzeitList(ctx);
        for (YahrzeitEntry entry : list)
            prefs(ctx).edit().putLong("processed_yahrzeit_" + entry.name, 0).apply();
    }

    // -----------------------------
    //  Yahrzeit List (JSON array)
    // -----------------------------
    public static JSONArray getYahrzeitList(Context ctx) {
        String json = prefs(ctx).getString(KEY_YAHRZEIT_LIST, "[]");
        try {
            return new JSONArray(json);
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    public static void saveYahrzeitList(Context ctx, JSONArray arr) {
        prefs(ctx).edit().putString(KEY_YAHRZEIT_LIST, arr.toString()).apply();
    }
    public static void saveYahrzeitList(Context context, List<YahrzeitEntry> list) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list.removeIf(e ->
                    e.name == null || e.name.trim().isEmpty() ||
                            e.diedDate == null
            );
        }
        Gson gson = new Gson();
        String json = gson.toJson(list);

        prefs(context).edit().putString(KEY_YAHRZEIT_LIST, json).apply();
    }

    public static List<YahrzeitEntry> loadYahrzeitList(Context context) {
        String json = prefs(context).getString(KEY_YAHRZEIT_LIST, null);

        if (json == null) return new ArrayList<>();

        Gson gson = new Gson();
        Type type = new TypeToken<List<YahrzeitEntry>>() {
        }.getType();
        List<YahrzeitEntry> list = gson.fromJson(json, type);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            list.removeIf(e ->
                    e.name == null || e.name.trim().isEmpty() ||
                            e.diedDate == null
            );
        }
        return list;
    }

    public static void removeYahrzeit(Context ctx, int index) {
        JSONArray arr = getYahrzeitList(ctx);
        JSONArray newArr = new JSONArray();

        for (int i = 0; i < arr.length(); i++) {
            if (i != index) {
                newArr.put(arr.optJSONObject(i));
            }
        }

        saveYahrzeitList(ctx, newArr);
    }
    public static Uri getYahrzeitRingtone(Context context) {
        String uriString = prefs(context).getString("yahrzeit_ringtone", null);

        if (uriString != null && !uriString.trim().isEmpty()) {
            return Uri.parse(uriString);
        }
        // Fallback to system default alarm sound
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    }

    public static void setYahrzeitRingtone(Context context, Uri uri) {
        prefs(context).edit().putString("yahrzeit_ringtone", uri.toString()).apply();
    }

    public static Uri getShabbatRingtone(Context context) {
        String uriString = prefs(context).getString("shabbat_ringtone", null);

        if (uriString != null && !uriString.trim().isEmpty()) {
            return Uri.parse(uriString);
        }
        // Fallback to system default alarm sound
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
    }

    public static void setShabbatRingtone(Context context, Uri uri) {
        prefs(context).edit().putString("shabbat_ringtone", uri.toString()).apply();
    }

    // -----------------------------
    //  Internal
    // -----------------------------
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    public static String getTimestamp(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("h:mm a", Locale.getDefault());
        return sdf.format(new Date(millis));
    }
    public static String getLogTime(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM d h:mm a", Locale.getDefault());
        return sdf.format(new Date(millis));
    }
    public static void log(String msg) {
        Log.d("Tizcoret Debug", msg);
        if(!isDebug()) {
            String log = logprefs.getString("log", "");
            logprefs.edit().putString("log", log + "\n" + msg).apply();
        }
    }

    public static boolean isDebug() {
        return false && BuildConfig.DEBUG;
    }

    public static void setPrefs(SharedPreferences p) {
        logprefs = p;
    }
}
