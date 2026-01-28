package com.emhillstudio.tizcoret;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class UserSettings {

    public static final String PREFS = "prefs";
    private static final String KEY_SHABBAT_JSON = "last_shabbat_json";
    private static final String KEY_LAT = "latitude";
    private static final String KEY_LNG = "longitude";
    private static final String KEY_SHABBAT_ALARM = "shabbat_alarm_enabled";
    private static final String KEY_YAHRZEIT_LIST = "yahrzeit_list";
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
        Type type = new TypeToken<List<YahrzeitEntry>>(){}.getType();
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
    public static void setLastShabbatJson(Context context, String json) {
        prefs(context)
                .edit()
                .putString(KEY_SHABBAT_JSON, json)
                .apply();
    }

    public static String getLastShabbatJson(Context context) {
        return prefs(context).getString(KEY_SHABBAT_JSON, null);
    }
    public static void saveYahrzeitJson(Context context, String json) {
        // Load existing list
        List<String> list = loadYahrzeitJsonList(context);
        if (list == null) list = new ArrayList<>();

        // Parse new payload
        Map<String, Object> newPayload = new Gson().fromJson(json, Map.class);
        int newId = ((Number) newPayload.get("entry_id")).intValue();

        // Remove any existing entry with the same entry_id
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            Map<String, Object> p = new Gson().fromJson(it.next(), Map.class);
            int id = ((Number) p.get("entry_id")).intValue();
            if (id == newId) {
                it.remove();
                break;
            }
        }

        // Add the new JSON
        list.add(json);

        // Save back
        prefs(context).edit().putString("yahrzeit_json_list", new Gson().toJson(list)).apply();
    }
    public static List<String> loadYahrzeitJsonList(Context context) {
        String json = prefs(context).getString("yahrzeit_json_list", null);
        if (json == null) return new ArrayList<>();

        Type type = new TypeToken<List<String>>(){}.getType();
        return new Gson().fromJson(json, type);
    }
    public static void clearAllYahrzeitJson(Context context) {
        prefs(context).edit().remove(UserSettings.KEY_YAHRZEIT_LIST).apply();
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
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
