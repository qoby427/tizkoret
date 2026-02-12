package com.emhillstudio.tizcoret;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class EventManager {
    private static class EventInfo {
        public EventType type;
        public long eventTime;
        public String payloadJson;
        public AlarmEntry early;
        public AlarmEntry final5;
        public int requestCodeBase;
        public Class<?> receiverClass() {
            return type == EventType.SHABBAT
                    ? ShabbatAlarmReceiver.class
                    : YahrzeitAlarmReceiver.class;
        }
    }
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

    private static final Map<Class<?>, EventMeta> META =
            Map.of(
                    ShabbatAlarmReceiver.class,
                    new EventMeta(3001, "SHABBAT_3HR_NOTIFICATION", "SHABBAT_5MIN_ALARM"),

                    YahrzeitAlarmReceiver.class,
                    new EventMeta(4001, "YAHRZEIT_3HR_NOTIFICATION", "YAHRZEIT_5MIN_ALARM")
            );

    private final Context ctx;

    public EventManager(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    // ------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------

    public void scheduleIfNeeded() {
        List<EventInfo> events = computeEvents();
        for (EventInfo e : events) {
            savePayload(e);
            AlarmUtils.scheduleEvent(ctx, e);
        }
    }

    public void cancelAll() {
        List<EventInfo> events = computeEvents();
        for (EventInfo e : events) {
            AlarmUtils.cancelEvent(ctx, e);
        }
    }

    // ------------------------------------------------------------
    // GENERIC EVENT COMPUTATION
    // ------------------------------------------------------------

    private List<EventInfo> computeEvents() {
        List<EventInfo> list = new ArrayList<>();

        // SHABBAT
        long candleTime = HebrewUtils.computeNextCandleLighting(ctx));

        list.add(buildEvent(
                EventType.SHABBAT,
                candleTime,
                3001,
                "SHABBAT_3HR_NOTIFICATION",
                "SHABBAT_5MIN_ALARM",
                ShabbatAlarmReceiver.class,
                buildShabbatMessage(shabbatTime)
        ));

        EventInfo yz = buildYahrzeitEvent();
        if(yz != null) {
            list.add(yz);
        }
        return list;
    }
    private EventInfo buildYahrzeitEvent() {
        List<YahrzeitEntry> list = UserSettings.loadYahrzeitList(ctx);
        if (list.isEmpty()) return null;

        Calendar today = Calendar.getInstance();

        StringBuilder msg = new StringBuilder();
        int count = 0;

        for (YahrzeitEntry entry : list) {
            if (entry.inYear == null) continue;

            Calendar c = Calendar.getInstance();
            c.setTime(entry.inYear);

            if (c.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                c.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)) {

                if (count > 0) msg.append(", ");
                msg.append(entry.name);
                count++;
            }
        }

        if (count == 0) return null;   // no yahrzeit today

        long candleTime = HebrewUtils.computeNextCandleLighting(ctx));

        String message = (count == 1)
                ? msg.toString() + "’s yahrzeit is today"
                : "Today’s yahrzeits: " + msg;

        return buildEvent(
                EventType.YAHRZEIT,
                candleTime,
                4001,
                "YAHRZEIT_3HR_NOTIFICATION",
                "YAHRZEIT_5MIN_ALARM",
                YahrzeitAlarmReceiver.class,
                message
        );
    }

    // ------------------------------------------------------------
    // GENERIC EVENT BUILDER
    // ------------------------------------------------------------

    private EventInfo buildEvent(
            EventType type,
            long eventTime,
            int requestCodeBase,
            String earlyAction,
            String finalAction,
            Class<?> receiverClass,
            String message
    ) {
        EventInfo e = new EventInfo();
        e.type = type;
        e.eventTime = eventTime;
        e.requestCodeBase = requestCodeBase;
        e.receiverOverride = receiverClass;   // optional override

        // Early
        e.early = new AlarmEntry();
        e.early.triggerAt = eventTime - 3 * 3600_000L;
        e.early.action = earlyAction;
        e.early.requestCode = requestCodeBase;

        // Final
        e.final5 = new AlarmEntry();
        e.final5.triggerAt = eventTime - 5 * 60_000L;
        e.final5.action = finalAction;
        e.final5.requestCode = requestCodeBase + 1;

        // Payload
        e.payloadJson = buildPayloadJson(e, message);

        return e;
    }

    // ------------------------------------------------------------
    // MESSAGE BUILDERS
    // ------------------------------------------------------------

    private String buildShabbatMessage(long eventTime) {
        return "Shabbat begins at " + UserSettings.getTimestamp(eventTime);
    }

    private String buildYahrzeitMessage(long eventTime) {
        return "Yahrzeit is at " + UserSettings.getTimestamp(eventTime);
    }

    // ------------------------------------------------------------
    // PAYLOAD
    // ------------------------------------------------------------

    private String buildPayloadJson(EventInfo e, String message) {
        try {
            JSONObject obj = new JSONObject();
            obj.put("event_type", e.type.toString());
            obj.put("event_time", e.eventTime);
            obj.put("early_time", e.early.triggerAt);
            obj.put("final_time", e.final5.triggerAt);
            obj.put("message", message);
            return obj.toString();
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void savePayload(EventInfo e) {
        SharedPreferences prefs = ctx.getSharedPreferences(UserSettings.PREFS, Context.MODE_PRIVATE);
        prefs.edit()
                .putString("payload_" + e.type, e.payloadJson)
                .apply();
    }
}
