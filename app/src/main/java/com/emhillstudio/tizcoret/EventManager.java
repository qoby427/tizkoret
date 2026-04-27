package com.emhillstudio.tizcoret;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.gson.Gson;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EventManager {

    // ------------------------------------------------------------
    // INTERNAL EVENT TYPE
    // ------------------------------------------------------------

    public static final int SHABBAT = 3001;
    public static final int YAHRZEIT = 4001;
    public static final int MASTER = 5001;
    private boolean immediately = false;
    private List<YahrzeitEntry> entries;
    private static SharedPreferences prefs;
    public static SharedPreferences getPrefs() {
        return prefs;
    }
    public interface LocationListener {
        void onLocationAvailable(Location loc);
        void onLocationUnavailable();
    }

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
        public int type;
        public long eventTime;
        public long eventId;
        public String name;
        public AlarmEntry early;
        public AlarmEntry final5;
        public YahrzeitEntry yzentry;
        public Class<?> receiverClass() {
            return type == SHABBAT
                    ? ShabbatAlarmReceiver.class
                    : YahrzeitAlarmReceiver.class;
        }
        public String message() {
            return type == SHABBAT
                    ? ""
                    : name.strip()+"'s Yahrzeit tomorrow. ";
        }
    }

    // ------------------------------------------------------------
    // INSTANCE STATE
    // ------------------------------------------------------------
    private static EventManager instance;
    private final Context ctx;
    private final ShabbatHelper helper;


    private EventManager(Context context) {
        ctx = context.getApplicationContext();
        prefs = ctx.getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);
        helper = new ShabbatHelper(ctx);

        List<YahrzeitEntry> saved = UserSettings.loadYahrzeitList(ctx);
        setEntries(saved);
    }
    // First-time initialization
    public static void init(Context context) {
        if (instance == null) {
            instance = new EventManager(context);
        }
    }
    public static EventManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("EventManager.init(context) not called");
        }
        return instance;
    }
    // ------------------------------------------------------------
    // PUBLIC API
    // ------------------------------------------------------------
    public void scheduleImmediately() {
        immediately = true;
        scheduleIfNeeded();
        immediately = false;
    }
    public void scheduleIfNeeded() {
        UserSettings.log("");
        UserSettings.log("EventManager::scheduleIfNeeded: Starting master planning +++++++++++++++++++++++++++++++++");
        schedule();
    }
    public void scheduleIfNeeded(String json) {
        if (json == null) {
            UserSettings.log("EventManager::scheduleIfNeeded: null json received");
            return;
        }

        EventManager.EventInfo e = new Gson().fromJson(json, EventManager.EventInfo.class);

        UserSettings.log("EventManager::scheduleIfNeeded: Starting " +
            e.receiverClass().getSimpleName() + " ---------------------------------");

        getCoarseLocationSmart(ctx, new LocationListener() {
            @Override
            public void onLocationAvailable(Location loc) {
                double oldLat = UserSettings.getLatitude(ctx);
                double oldLng = UserSettings.getLongitude(ctx);
                UserSettings.log("EventManager::scheduleIfNeeded - old location " + oldLat + ", " + oldLng);
                UserSettings.log("EventManager::scheduleIfNeeded - new location " + loc.getLatitude() + ", " + loc.getLongitude());

                float[] result = new float[1];
                Location.distanceBetween(oldLat, oldLng, loc.getLatitude(), loc.getLongitude(), result);
                if(result[0] > 20000)
                {
                    UserSettings.setLatitude(ctx, loc.getLatitude());
                    UserSettings.setLongitude(ctx, loc.getLongitude());
                    UserSettings.log("EventManager::scheduleIfNeeded - Using new location " + loc.getLatitude() + ", " + loc.getLongitude());

                    sendLocationChangedNotification(loc);
                }
                else
                    UserSettings.log("EventManager::scheduleIfNeeded - Using location " + oldLat + ", " + oldLng);
                schedule(e);
            }
            @Override
            public void onLocationUnavailable() {
                UserSettings.log("EventManager::scheduleIfNeeded - Coarse location unavailable");
                schedule(e);
            }
        });
    }
    public void setEntries(List<YahrzeitEntry> newEntries) {
        entries = newEntries;
    }
    public void schedule() {
        boolean newEvent = false;
        List<EventInfo> events = new ArrayList<>();
        computeEvents(events);

        for (EventInfo e : events) {
            if(!UserSettings.isDebug()) {
                long eventId = helper.insertCalendarEvent(e);
                if(eventId == -1) {
                    eventId = helper.updateCalendarEvent(e);
                }
                if (eventId != 0 && e.yzentry != null) {
                    e.yzentry.eventId = eventId;
                    newEvent = true;

                    UserSettings.log("EventManager::schedule: added " + e.receiverClass().getSimpleName() +
                        " at " + UserSettings.getLogTime(e.eventTime) + ". Event ID=" + eventId);
                }
            }
            AlarmUtils.scheduleMasterEvent(ctx, e, immediately);
        }

        if(newEvent) {
            UserSettings.saveYahrzeitList(ctx, entries);
        }
    }
    private void schedule(EventInfo e) {
        AlarmUtils.scheduleEntry(ctx, e);
    }
    private EventInfo toEventInfo(YahrzeitEntry entry) {
        EventInfo info = new EventInfo();
        info.type = YAHRZEIT;
        info.name = entry.name;

        info.early = new AlarmEntry();
        info.early.requestCode = getEarlyReqCode(YAHRZEIT, entry.name);
        info.early.action = "yahrzeit_notification";

        info.final5 = new AlarmEntry();
        info.final5.requestCode = getFinalReqCode(YAHRZEIT, entry.name);
        info.final5.action = "yahrzeit_alarm";

        info.yzentry = entry;

        return info;
    }
    private EventInfo toEventInfo() {
        EventInfo info = new EventInfo();
        info.type = SHABBAT;
        info.name = "";

        info.early = new AlarmEntry();
        info.early.requestCode = getEarlyReqCode(SHABBAT, "Shabbat");
        info.early.action = "shabbat_notification";

        info.final5 = new AlarmEntry();
        info.final5.requestCode = getFinalReqCode(SHABBAT, "Shabbat");
        info.final5.action = "shabbat_alarm";

        info.yzentry = null;

        return info;
    }
    public void cancelAll() {
        cancelShabbatEvents();
        cancelAllYahrzeitEvents();
    }
    public void cancelShabbatEvents() {
        EventInfo info = toEventInfo();
        AlarmUtils.cancelMaster(ctx, info);
        AlarmUtils.cancelEntry(ctx, info);
    }
    public void cancelAllYahrzeitEvents() {
        for (YahrzeitEntry entry : entries) {
            cancelYahrzeitEvent(entry);
        }
    }
    public void cancelYahrzeitEvent(YahrzeitEntry entry) {
        EventInfo info = toEventInfo(entry);
        AlarmUtils.cancelMaster(ctx, info);
        AlarmUtils.cancelEntry(ctx, info);
        helper.removeCalendarEvent(entry.eventId);
    }

    // ------------------------------------------------------------
    // GENERIC EVENT COMPUTATION
    // ------------------------------------------------------------
    private void computeEvents(List<EventInfo> list) {
        buildShabbatEvent(list);
        buildYahrzeitEvents(list);
    }
    private void buildShabbatEvent(List<EventInfo> list) {
        long candleTime = new ShabbatHelper(ctx).computeNextCandleLighting();

        UserSettings.log("EventManager::computeEvents: next candle time " + UserSettings.getLogTime(candleTime));

        long processed_shabbat = prefs.getLong("processed_shabbat_time", 0);
        if(processed_shabbat > 0)
            UserSettings.log("EventManager::computeEvents: processed candle time " + UserSettings.getLogTime(processed_shabbat));

        if (UserSettings.isDebug() || immediately || processed_shabbat < candleTime) {
            prefs.edit().putLong("processed_shabbat_time", candleTime).apply();

            list.add(buildEvent(
                    SHABBAT,
                    candleTime,
                    buildShabbatMessage(candleTime)
            ));
        }
    }
    private void buildYahrzeitEvents(List<EventInfo> ret) {
        for (YahrzeitEntry entry : entries) {
            entry.inYear = HebrewUtils.nextYahrzeit(entry.diedDate);
            long current_yahrzeit = HebrewUtils.computeYahrzeitCandleLighting(ctx, entry.inYear);
            long processed_yahrzeit = prefs.getLong("processed_yahrzeit_"+entry.name, 0);

            if (UserSettings.isDebug() || immediately || processed_yahrzeit < current_yahrzeit) {
                prefs.edit().putLong("processed_yahrzeit_" + entry.name, current_yahrzeit).apply();

                ret.add(buildEvent(YAHRZEIT,
                        current_yahrzeit,
                        entry));
            }
        }
    }

    // ------------------------------------------------------------
    // GENERIC EVENT BUILDER (uses metadata map)
    // ------------------------------------------------------------
    private static int hashName(String name) {
        return Math.abs(name.trim().toLowerCase().hashCode() % 10000);
    }
    public static int getMasterReqCode(EventInfo e) {
        return MASTER + hashName("master" + e.early.requestCode + e.final5.requestCode);
    }
    private int getEarlyReqCode(int type, String name) {
        return type + hashName("early" + name.split(" ")[0]);
    }

    private int getFinalReqCode(int type, String name) {
        return type + hashName("final" + name.split(" ")[0]);
    }
    private EventInfo buildEvent(int type, long eventTime, YahrzeitEntry entry) {
        EventInfo e = buildEvent(type, eventTime, entry.name);
        if(e != null)
            e.yzentry = entry;
        return  e;
    }
    private EventInfo buildEvent(int type, long eventTime, String name) {
        EventInfo e = new EventInfo();
        e.type = type;
        e.eventTime = eventTime;
        e.name = name;

        EventMeta meta = META.get(e.receiverClass());
        if(meta == null)
            return null;

        // Early
        e.early = new AlarmEntry();
        e.early.triggerAt = eventTime - (UserSettings.isDebug() ? 12 * 60_000L : 3 * 3600_000L);
        e.early.action = meta.earlyAction;
        e.early.requestCode = getEarlyReqCode(type, name);

        // Final
        e.final5 = new AlarmEntry();
        e.final5.triggerAt = eventTime - (UserSettings.isDebug() ? 10 * 60_000L : 5 * 60_000L);
        e.final5.action = meta.finalAction;
        e.final5.requestCode = getFinalReqCode(type, name);

        // Payload
        buildPayloadJson(e, type == SHABBAT ? buildShabbatMessage(eventTime) : name + "’s yahrzeit is tomorrow");
        String msg = type == SHABBAT ? "Shabbat" : "yahrzeit for "+name;
        UserSettings.log("EventManager::buildEvent " + msg + ": notif at " + UserSettings.getLogTime(e.early.triggerAt) +
            " alarm at " + UserSettings.getLogTime(e.final5.triggerAt));
        UserSettings.log("EventManager::buildEvent req codes " + e.early.requestCode + " " + e.final5.requestCode);

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
            obj.put("event", e.type == SHABBAT ? "Shabbat" : "Yahrzeit");
            obj.put("request_code", e.early.requestCode);
            obj.put("next_candle_time", e.eventTime);
            obj.put("3hour_notif_time", e.early.triggerAt);
            obj.put("message", message);
            obj.put("event_type", "notification");
            e.early.payloadJson =  obj.toString();

            obj = new JSONObject();
            obj.put("event", e.type == SHABBAT ? "Shabbat" : "Yahrzeit");
            obj.put("request_code", e.final5.requestCode);
            obj.put("notification_request_code", e.early.requestCode);
            obj.put("next_candle_time", e.eventTime);
            obj.put("5min_alarm_time", e.final5.triggerAt);
            obj.put("message", message);
            obj.put("event_type", "alarm");
            e.final5.payloadJson =  obj.toString();
        } catch (Exception ex) {
            UserSettings.log("EventManager::buildPayloadJson: " + ex);
        }
    }
    @SuppressLint("MissingPermission")
    public void getCoarseLocationSmart(Context ctx, LocationListener listener) {
        FusedLocationProviderClient fused =
                LocationServices.getFusedLocationProviderClient(ctx);

        // 1. Try cached fused location (allowed everywhere)
        fused.getLastLocation().addOnSuccessListener(last -> {
            if (last != null) {
                listener.onLocationAvailable(last);
                return;
            }

            // 2. Try passive location (your new primary source)
            Location passive = PassiveLocationStore.get();
            if (passive != null) {
                listener.onLocationAvailable(passive);
                return;
            }

            // 3. No location available
            listener.onLocationUnavailable();
        }).addOnFailureListener(e -> {
            // Failure retrieving last location
            Location passive = PassiveLocationStore.get();
            if (passive != null)
                listener.onLocationAvailable(passive);
            else
                listener.onLocationUnavailable();
        });
    }

    @SuppressLint("MissingPermission")
    private void sendLocationChangedNotification(Location newLoc) {
        String loc = resolveLocationName(newLoc);
        NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, "location_changes")
                .setSmallIcon(R.drawable.ic_location)
                .setContentTitle("New location detected. Candle lighting time may change")
                .setContentText("\nYour new location is " + loc)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        NotificationManagerCompat.from(ctx).notify(12001, b.build());
    }
    private String resolveLocationName(Location loc) {
        try {
            Geocoder geocoder = new Geocoder(ctx, Locale.getDefault());
            List<Address> list = geocoder.getFromLocation(
                    loc.getLatitude(),
                    loc.getLongitude(),
                    1
            );

            if (list != null && !list.isEmpty()) {
                Address a = list.get(0);

                // Prefer locality (city)
                if (a.getLocality() != null) return a.getLocality();

                // Fallback to admin area (state/province)
                if (a.getAdminArea() != null) return a.getAdminArea();

                // Fallback to country
                if (a.getCountryName() != null) return a.getCountryName();
            }

        } catch (Exception ignored) {
            // Geocoder can throw IOException or IllegalArgumentException
        }
        return loc.getLongitude() + ", " + loc.getLatitude();
    }
}
