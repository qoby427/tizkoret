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

    private EventInfo toEventInfo(YahrzeitEntry entry) {
        EventInfo info = new EventInfo();
        info.type = YAHRZEIT;

        info.early = new AlarmEntry();
        info.early.requestCode = getEarlyReqCode(YAHRZEIT, entry.name);
        info.early.action = "yahrzeit_notification";

        info.final5 = new AlarmEntry();
        info.final5.requestCode = getFinalReqCode(YAHRZEIT, entry.name);
        info.final5.action = "yahrzeit_alarm";

        return info;
    }
    private EventInfo toEventInfo() {
        EventInfo info = new EventInfo();
        info.type = SHABBAT;

        info.early = new AlarmEntry();
        info.early.requestCode = getEarlyReqCode(SHABBAT, "shabbat");
        info.early.action = "shabbat_notification";

        info.final5 = new AlarmEntry();
        info.final5.requestCode = getFinalReqCode(SHABBAT, "shabbat");
        info.final5.action = "shabbat_alarm";

        return info;
    }
    public void cancelAll() {
        // Cancel Shabbat
        AlarmUtils.cancelEntry(ctx, toEventInfo());

        // Cancel Yahrzeit alarms
        List<YahrzeitEntry> list = UserSettings.loadYahrzeitList(ctx);
        for (YahrzeitEntry entry : list) {
            AlarmUtils.cancelEntry(ctx, toEventInfo(entry));
        }
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
                candleTime = System.currentTimeMillis() + 15 * 60_000;
            } else {
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

        if (UserSettings.isDebug() || processed_shabbat < candleTime) {
            prefs.edit().putLong("processed_shabbat_time", candleTime).apply();

            list.add(buildEvent(
                    SHABBAT,
                    candleTime,
                    buildShabbatMessage(candleTime)
            ));
        }

        // YAHRZEIT
        buildYahrzeitEvents(list);

        return list;
    }
    private void buildYahrzeitEvents(List<EventInfo> ret) {
        List<YahrzeitEntry> list = UserSettings.loadYahrzeitList(ctx);

        for (YahrzeitEntry entry : list) {
            entry.inYear = HebrewUtils.nextYahrzeit(entry.diedDate);
            long current_yahrzeit = HebrewUtils.computeYahrzeitCandleLighting(ctx, entry.inYear);
            long processed_yahrzeit = prefs.getLong("processed_yahrzeit_"+entry.name, 0);

            if (UserSettings.isDebug() || processed_yahrzeit < current_yahrzeit) {
                prefs.edit().putLong("processed_yahrzeit_" + entry.name, current_yahrzeit).apply();

                ret.add(buildEvent(YAHRZEIT,
                        current_yahrzeit,
                        entry.name));
            }
        }
        UserSettings.saveYahrzeitList(ctx, list);
    }

    // ------------------------------------------------------------
    // GENERIC EVENT BUILDER (uses metadata map)
    // ------------------------------------------------------------
    private int hashName(String name) {
        return Math.abs(name.trim().toLowerCase().hashCode() % 1000);
    }

    private int getEarlyReqCode(int type, String name) {
        return type + hashName("early"+name);
    }

    private int getFinalReqCode(int type, String name) {
        return type + hashName("final"+name);
    }

    private EventInfo buildEvent(int type, long eventTime, String name) {
        EventInfo e = new EventInfo();
        e.type = type;
        e.eventTime = eventTime;

        EventMeta meta = META.get(e.receiverClass());
        if(meta == null)
            return null;

        // Early
        e.early = new AlarmEntry();
        e.early.triggerAt = eventTime - (UserSettings.isDebug() ? 13 * 60_000L : 3 * 3600_000L);
        e.early.action = meta.earlyAction;
        e.early.requestCode = getEarlyReqCode(type, name);

        // Final
        e.final5 = new AlarmEntry();
        e.final5.triggerAt = eventTime - (UserSettings.isDebug() ? 11 * 60_000L : 5 * 60_000L);
        e.final5.action = meta.finalAction;
        e.final5.requestCode = getFinalReqCode(type, name);

        // Payload
        buildPayloadJson(e, type == SHABBAT ? buildShabbatMessage(eventTime) : name + "’s yahrzeit is tomorrow");
        String msg = type == SHABBAT ? "shabbat" : "yahrzeit for "+name;
        UserSettings.log("EventManager::buildEvent " + msg + ": notif at " + UserSettings.getLogTime(e.early.triggerAt) +
            " alarm at " + UserSettings.getLogTime(e.final5.triggerAt));

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
            obj.put("request_code", e.early.requestCode);
            obj.put("next_candle_time", e.eventTime);
            obj.put("3hour_notif_time", e.early.triggerAt);
            obj.put("message", message);
            obj.put("event_type", "notification");
            e.early.payloadJson =  obj.toString();

            obj = new JSONObject();
            obj.put("event", e.type == SHABBAT ? "shabbat" : "yahrzeit");
            obj.put("request_code", e.final5.requestCode);
            obj.put("notification_request_code", e.early.requestCode);
            obj.put("next_candle_time", e.eventTime);
            obj.put("5min_alarm_time", e.final5.triggerAt);
            obj.put("message", message);
            obj.put("event_type", "alarm");
            e.final5.payloadJson =  obj.toString();
        } catch (Exception ex) {
        }
    }
}
