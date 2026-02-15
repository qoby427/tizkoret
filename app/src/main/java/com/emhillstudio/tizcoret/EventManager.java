package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class EventManager {

    // ------------------------------------------------------------
    // INTERNAL EVENT TYPE
    // ------------------------------------------------------------

    public static final int SHABBAT = 3001;
    public static final int YAHRZEIT = 4001;
    private static SharedPreferences prefs;

    // ------------------------------------------------------------
    // INTERNAL METADATA
    // ------------------------------------------------------------

    private static class EventMeta {
        final int base;
        final String earlyAction;
        final String finalAction;

        EventMeta(int base, String early, String fin) {
            this.base = base;
            this.earlyAction = early;
            this.finalAction = fin;
        }
    }

    // keyed by receiver class, as you wanted
    private static final Map<Class<?>, EventMeta> META =
            Map.of(
                    ShabbatAlarmReceiver.class,
                    new EventMeta(SHABBAT, "shabbat_notification", "shabbat_alarm"),

                    YahrzeitAlarmReceiver.class,
                    new EventMeta(YAHRZEIT, "yahrzeit_notification", "yahrzeit_alarm")
            );

    // ------------------------------------------------------------
    // INTERNAL DATA CLASSES (used by AlarmUtils)
    // ------------------------------------------------------------

    public static class AlarmEntry {
        public long triggerAt;
        public String action;
        public int requestCode;
        public String payloadJson;
    }

    public static class EventInfo {
        private int type;
        public long eventTime;
        public AlarmEntry early;
        public AlarmEntry final5;
        public int requestCodeBase;

        public Class<?> receiverClass() {
            return type == SHABBAT
                    ? ShabbatAlarmReceiver.class
                    : YahrzeitAlarmReceiver.class;
        }
    }

    // ------------------------------------------------------------
    // INSTANCE STATE
    // ------------------------------------------------------------

    private final Context ctx;

    public EventManager(Context context) {
        ctx = context.getApplicationContext();
        prefs = ctx.getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);
    }

    // ------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------

    public void scheduleIfNeeded() {
        UserSettings.log("EventManager::scheduleIfNeeded: Starting event planning ---------------------------------");
        List<EventInfo> events = computeEvents();
        for (EventInfo e : events) {
            AlarmUtils.scheduleEntry(ctx, e);
        }
    }

    public void cancelAll() {
        List<EventInfo> events = computeEvents();
        for (EventInfo e : events) {
            AlarmUtils.cancelEntry(ctx, e);
        }
        prefs.edit().putLong("debug_last_candle", 0).apply();
        prefs.edit().putLong("processed_shabbat_time", 0).apply();
        List<YahrzeitEntry> list = UserSettings.loadYahrzeitList(ctx);
        for (YahrzeitEntry entry : list)
            prefs.edit().putLong("processed_yahrzeit_" + entry.name, 0).apply();
    }

    // ------------------------------------------------------------
    // GENERIC EVENT COMPUTATION
    // ------------------------------------------------------------

    private List<EventInfo> computeEvents() {
        List<EventInfo> list = new ArrayList<>();

        long candleTime;
        if(UserSettings.isDebug()) {
            long last = prefs.getLong("debug_last_candle", 0);

            if (last == 0) {
                // first run
                candleTime = System.currentTimeMillis() + 15 * 60_000;
            } else {
                // next cycles
                candleTime = last + 15 * 60_000;
            }
            prefs.edit().putLong("debug_last_candle", candleTime).apply();
        }
        else
            candleTime = HebrewUtils.computeNextCandleLighting(ctx);

        UserSettings.log("EventManager::computeEvents: next candle time "+UserSettings.getLogTime(candleTime));

        // SHABBAT
        long processed_shabbat = prefs.getLong("processed_shabbat_time", 0);
        UserSettings.log("EventManager::computeEvents: processed candle time "+UserSettings.getLogTime(processed_shabbat));

        if (processed_shabbat < candleTime) {
            prefs.edit().putLong("processed_shabbat_time", candleTime).apply();

            list.add(buildEvent(
                    SHABBAT,
                    candleTime,
                    buildShabbatMessage(candleTime)
            ));
        }

        // YAHRZEIT
        EventInfo yz = buildYahrzeitEvent(candleTime);
        if (yz != null) {
            list.add(yz);
        }

        return list;
    }
    private EventInfo buildYahrzeitEvent(long candleTime) {
        List<YahrzeitEntry> list = UserSettings.loadYahrzeitList(ctx);
        if (list.isEmpty()) return null;

        Calendar today = Calendar.getInstance();
        int year = today.get(Calendar.YEAR);
        int dayOfYear = today.get(Calendar.DAY_OF_YEAR);

        StringBuilder names = new StringBuilder();
        int count = 0;
        boolean found = false;
        for (YahrzeitEntry entry : list) {
            Calendar c = Calendar.getInstance();
            c.setTime(entry.inYear);

            boolean isToday = UserSettings.isDebug() ||
                c.get(Calendar.YEAR) == year &&
                c.get(Calendar.DAY_OF_YEAR) == dayOfYear;

            if (isToday) {
                if (count > 0) names.append(", ");
                names.append(entry.name.trim());
                count++;
            }

            long processed_yahrzeit = prefs.getLong("processed_yahrzeit_"+entry.name, 0);
            if (isToday && processed_yahrzeit < candleTime) {
                prefs.edit().putLong("processed_yahrzeit_" + entry.name, candleTime).apply();
                found = true;
            }
        }

        if (count == 0 || !found) return null;

        String message = (count == 1)
                ? names + "’s yahrzeit is tomorrow"
                : "Tomorrow’s yahrzeits: " + names;
        message += ".\nCandle lighting tonight at " + UserSettings.getTimestamp(candleTime);

        return buildEvent(YAHRZEIT, candleTime, message);
    }

    // ------------------------------------------------------------
    // GENERIC EVENT BUILDER (uses metadata map)
    // ------------------------------------------------------------

    private EventInfo buildEvent(int type, long eventTime, String message) {
        EventInfo e = new EventInfo();
        e.type = type;
        e.eventTime = eventTime;

        Class<?> receiver = e.receiverClass();
        EventMeta meta = META.get(receiver);
        if(meta == null)
            return null;

        e.requestCodeBase = meta.base;

        // Early
        e.early = new AlarmEntry();
        e.early.triggerAt = eventTime - (UserSettings.isDebug() ? 13 * 60_000L : 3 * 3600_000L);
        e.early.action = meta.earlyAction;
        e.early.requestCode = meta.base;

        // Final
        e.final5 = new AlarmEntry();
        e.final5.triggerAt = eventTime - (UserSettings.isDebug() ? 11 * 60_000L : 5 * 60_000L);
        e.final5.action = meta.finalAction;
        e.final5.requestCode = meta.base + 1;

        // Payload
        buildPayloadJson(e, message);

        UserSettings.log("EventManager::buildEvent "+e.type+": notif at "+UserSettings.getLogTime(e.early.triggerAt)+
            " alarm at "+UserSettings.getLogTime(e.final5.triggerAt));

        return e;
    }

    // ------------------------------------------------------------
    // MESSAGE BUILDERS
    // ------------------------------------------------------------

    private String buildShabbatMessage(long eventTime) {
        return "Shabbat begins at " + UserSettings.getTimestamp(eventTime);
    }

    // ------------------------------------------------------------
    // PAYLOAD
    // ------------------------------------------------------------

    private void buildPayloadJson(EventInfo e, String message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("event", e.type == SHABBAT ? "shabbat" : "yahrzeit");
            obj.put("request_code", e.requestCodeBase);
            obj.put("next_candle_time", e.eventTime);
            obj.put("3hour_notif_time", e.early.triggerAt);
            obj.put("message", message);
            obj.put("event_type", "notification");
            e.early.payloadJson =  obj.toString();

            obj = new JSONObject();
            obj.put("event", e.type == SHABBAT ? "shabbat" : "yahrzeit");
            obj.put("request_code", e.requestCodeBase);
            obj.put("notification_request_code", e.requestCodeBase);
            obj.put("next_candle_time", e.eventTime);
            obj.put("5min_alarm_time", e.final5.triggerAt);
            obj.put("message", message);
            obj.put("event_type", "alarm");
            e.final5.payloadJson =  obj.toString();
        } catch (Exception ex) {
        }
    }
}
