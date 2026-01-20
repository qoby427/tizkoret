package com.emhillstudio.tizcoret;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class UserSettings {

    private static final String PREFS = "tizcoret_settings";

    private static final String KEY_LAT = "latitude";
    private static final String KEY_LNG = "longitude";

    private static final String KEY_SHABBAT_ALARM = "shabbat_alarm_enabled";

    private static final String KEY_YAHRZEIT_LIST = "yahrzeit_list";

    // ⭐ NEW: store next scheduled alarm time
    private static final String KEY_NEXT_ALARM = "next_alarm_time";

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

    // -----------------------------
    //  Next Alarm Time (NEW)
    // -----------------------------
    public static void setNextAlarmTime(Context ctx, long timeMillis) {
        prefs(ctx).edit().putLong(KEY_NEXT_ALARM, timeMillis).apply();
    }

    public static long getNextAlarmTime(Context ctx) {
        return prefs(ctx).getLong(KEY_NEXT_ALARM, -1);
    }

    public static void clearNextAlarmTime(Context ctx) {
        prefs(ctx).edit().remove(KEY_NEXT_ALARM).apply();
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

    public static void addYahrzeit(Context ctx, String name, String civilDate, String hebrewDate) {
        JSONArray arr = getYahrzeitList(ctx);

        JSONObject obj = new JSONObject();
        try {
            obj.put("name", name);
            obj.put("civilDate", civilDate);
            obj.put("hebrewDate", hebrewDate);
            arr.put(obj);
        } catch (JSONException ignored) {}

        saveYahrzeitList(ctx, arr);
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

    // -----------------------------
    //  Internal
    // -----------------------------
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
