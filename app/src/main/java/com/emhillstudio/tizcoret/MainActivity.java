package com.emhillstudio.tizcoret;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.button.MaterialButton;

import com.kosherjava.zmanim.ZmanimCalendar;
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.util.GeoLocation;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

@SuppressLint("MissingPermission")
public class MainActivity extends MessageActivity {

    private TextView shabbatStatus;
    private MaterialButton shabbatToggle;
    private RecyclerView yahrzeitList;
    private YahrzeitAdapter yahrzeitAdapter;
    private String timeZoneId;
    private List<YahrzeitEntry> list = new ArrayList<>();
    private static SharedPreferences prefs;
    private static final int RINGTONE_REQUEST_CODE = 1234;
    private static final int REQ_CALENDAR = 2001;
    private static final int REQ_LOCATION = 1001;
    private static final int SHABBAT_ALARM = 3001;

    private enum PendingAction {
        NONE,
        UPDATE_CALENDAR,
        ADD_SHABBAT_EVENTS,
    }
    private PendingAction pendingAction = PendingAction.NONE;
    private EventManager eventManager;
    @Override
    protected void onResume() {
        super.onResume();
        refreshYahrzeitList();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);
        prefs.edit().putLong("debug_last_candle", 0).apply();

        // -----------------------------
        //  UI references
        // -----------------------------
        shabbatStatus = findViewById(R.id.shabbatStatus);
        shabbatToggle = findViewById(R.id.shabbatToggleButton);
        yahrzeitList = findViewById(R.id.yahrzeitList);
        // -----------------------------
        //  Shabbat toggle
        // -----------------------------
        boolean enabled = UserSettings.isShabbatAlarmEnabled(this);
        updateShabbatUI(enabled);

        shabbatToggle.setOnClickListener(v -> {
            maybeAskToAddShabbat();
        });

        MaterialButton updateCalendarButton = findViewById(R.id.updateCalendarButton);

        // -----------------------------
        //  Yahrzeit table
        // -----------------------------
        yahrzeitList = findViewById(R.id.yahrzeitList);
        yahrzeitList.setLayoutManager(new LinearLayoutManager(this));
        yahrzeitAdapter = new YahrzeitAdapter(this, list);
        yahrzeitAdapter.setOnEntryChangedListener(entry -> {
            updateCalendarButton.setEnabled(!yahrzeitAdapter.getEntries().isEmpty());
            UserSettings.saveYahrzeitList(this, yahrzeitAdapter.getEntries());
        });

        yahrzeitList.setAdapter(yahrzeitAdapter);

        List<YahrzeitEntry> saved = UserSettings.loadYahrzeitList(this);
        yahrzeitAdapter.setEntries(saved);
        findViewById(R.id.addYahrzeitButton).setOnClickListener(v -> {
            yahrzeitAdapter.addEmptyRow();
        });

        findViewById(R.id.cancelYahrzeitButton).setOnClickListener(v -> {
            stopAllYahrzeits();
        });

        if (!prefs.contains("date_format")) {
            prefs.edit().putString("date_format", "MM/dd/yyyy").apply();
        }

        updateCalendarButton.setEnabled(!yahrzeitAdapter.getEntries().isEmpty());
        updateCalendarButton.setOnClickListener(v -> {
            pendingAction = PendingAction.UPDATE_CALENDAR;

            if (hasLocationPermission() && hasCalendarPermission()) {
                updateCalendar();
                if(UserSettings.isShabbatAlarmEnabled(this))
                    eventManager.scheduleIfNeeded();
                return;
            }

            requestLocation();
        });


        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR
                    },
                    REQ_CALENDAR
            );
        }

        timeZoneId = TimeZone.getDefault().getID();

        findViewById(R.id.buttonAbout).setOnClickListener(v -> {
            startActivity(new Intent(this, AboutActivity.class));
        });

        findViewById(R.id.buttonCustomize).setOnClickListener(v -> {
            startActivity(new Intent(this, CustomizeActivity.class));
        });

        findViewById(R.id.buttonSupport).setOnClickListener(v -> {
            startActivity(new Intent(this, SupportActivity.class));
        });

        EdgeToEdge.enable(
                this,
                SystemBarStyle.light(
                        /* backgroundColor = */ Color.TRANSPARENT,
                        /* foregroundColor = */ Color.BLACK
                ),
                SystemBarStyle.light(
                        /* backgroundColor = */ Color.TRANSPARENT,
                        /* foregroundColor = */ Color.BLACK
                )
        );

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        View content = findViewById(android.R.id.content);

        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        3001
                );
            }
        }

        eventManager = new EventManager(this);

        if(UserSettings.isDebug())
            UserSettings.clearEvents(this);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RINGTONE_REQUEST_CODE && resultCode == RESULT_OK) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

            if (uri != null) {
                prefs.edit().putString("alarm_ringtone", uri.toString()).apply();
            }
        }
    }
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private boolean hasCalendarPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void updateCalendar() {
        boolean done = true;
        String err = "Calendar updated";
        for (YahrzeitEntry entry : yahrzeitAdapter.getEntries()) {
            if(entry.name.isEmpty() || entry.diedDate.toString().isEmpty())
                continue;
            try {
                Calendar cal = Calendar.getInstance();
                cal.setTime(entry.inYear);
                cal.add(Calendar.DAY_OF_MONTH, -1);

                ZmanimCalendar zc = buildZmanimCalendar();
                zc.setCalendar(cal);

                Date candleLighting = zc.getCandleLighting();
                String msg = entry.name.strip()+"'s Yahrzeit tomorrow. ";
                if(insertCalendarEvent(candleLighting.getTime(), msg)) {
                    scheduleAlarm(candleLighting.getTime(), "yahrzeit",entry.diedDate);
                }
            } catch (Exception e) {
                String format = prefs.getString("date_format", "MM/dd/yyyy");
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                err = "Cannot parse date "+sdf.format(entry.inYear)+"\n"+ e;
                done = false;
                break;
            }
        }
        showMessage(err, done);
    }
    private boolean setCalendarPerms() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_CALENDAR)
                != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.READ_CALENDAR,
                            Manifest.permission.WRITE_CALENDAR
                    },
                    REQ_CALENDAR
            );
            return false; // STOP HERE
        }
        return true; // Permission already granted
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean granted = grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED;

        // 1️⃣ LOCATION permission result
        if (requestCode == REQ_LOCATION) {
            if (granted) {
                if(UserSettings.getLatitude(this) == 0 && UserSettings.getLongitude(this) == 0)
                    getLocationNow();
            }
            return;
        }

        // 2️⃣ CALENDAR permission result
        if (requestCode == REQ_CALENDAR) {
            if (granted) {
                if (pendingAction == PendingAction.ADD_SHABBAT_EVENTS) {
                    eventManager.scheduleIfNeeded();
                } else if (pendingAction == PendingAction.UPDATE_CALENDAR) {
                    updateCalendar();
                    if(UserSettings.isShabbatAlarmEnabled(this))
                        eventManager.scheduleIfNeeded();
                }
            }

            pendingAction = PendingAction.NONE;
        }
    }
    // -----------------------------
    //  Shabbat UI
    // -----------------------------
    private void updateShabbatUI(boolean enabled) {
        if (enabled) {
            shabbatStatus.setText("Shabbat candle lighting times alarm\nis turned on");
            shabbatToggle.setText("Turn Off");
        } else {
            shabbatStatus.setText("Shabbat candle lighting times alarm\nis turned off");
            shabbatToggle.setText("Turn On");
        }
    }

    // -----------------------------
    //  Shabbat calendar insertion
    // -----------------------------
    private void maybeAskToAddShabbat() {
        boolean enabled = UserSettings.isShabbatAlarmEnabled(this);
        UserSettings.setShabbatAlarmEnabled(this, !enabled);
        updateShabbatUI(!enabled);
        if (!enabled) {
            showQuestion("Add Shabbat Times",
                    "Would you like to add Shabbat times for next Friday to your calendar?",
                    () -> {
                        Intent serviceIntent = new Intent(this, AlarmService.class);
                        startService(serviceIntent);

                        pendingAction = PendingAction.ADD_SHABBAT_EVENTS;
                        if (hasLocationPermission() && hasCalendarPermission()) {
                            addNextFridayShabbatEvents();
                            eventManager.scheduleIfNeeded();
                            return;
                        }

                        requestLocation();
                    });
        }
        else {
            showQuestion("Add Shabbat Times",
                    "Would you like to remove Shabbat alarm?",
                    () -> {
                        cancelAlarm(
                                REQ_LOCATION,
                                ShabbatAlarmReceiver.class,
                                AlarmService.class
                        );
                        eventManager.cancelAll();
                    });
        }
    }
    private ZmanimCalendar buildZmanimCalendar() {
        double lat = UserSettings.getLatitude(this);
        double lng = UserSettings.getLongitude(this);

        TimeZone tz = Calendar.getInstance().getTimeZone();

        GeoLocation geo = new GeoLocation(
                "User",
                lat,
                lng,
                0,
                tz
        );

        return new ZmanimCalendar(geo);
    }
    private void addNextFridayShabbatEvents() {
        long candleLighting = HebrewUtils.computeNextCandleLighting(this);

        if(insertCalendarEvent(candleLighting,"")) {
            UserSettings.log("MainActivity::addNextFridayShabbatEvents: adding shabbat " +
                UserSettings.getLogTime(candleLighting));
        }
    }
    private boolean insertCalendarEvent(long candleLighting, String header) {
        long calendarId = getGoogleCalendarId();
        if (calendarId == -1)
            return false;

        ContentResolver cr = getContentResolver();

        // Format time for title
        String formatted = new SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(candleLighting);

        String title = header + "Candle Lighting – " + formatted;

        if (eventAlreadyExists(calendarId, candleLighting, title))
            return false;

        // 1. Insert event into EVENTS table
        ContentValues event = new ContentValues();

        event.put(CalendarContract.Events.TITLE, title);
        event.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        event.put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId);
        event.put(CalendarContract.Events.EVENT_END_TIMEZONE, timeZoneId);
        event.put(CalendarContract.Events.DTSTART, candleLighting);
        event.put(CalendarContract.Events.DTEND, candleLighting + 60 * 60 * 1000);

        Uri eventUri = cr.insert(CalendarContract.Events.CONTENT_URI, event);
        if (eventUri == null)
            return false;

        long eventId = Long.parseLong(eventUri.getLastPathSegment());

        // 2. Add your 5-minute reminder
        ContentValues reminder = new ContentValues();
        reminder.put(CalendarContract.Reminders.EVENT_ID, eventId);
        reminder.put(CalendarContract.Reminders.MINUTES, 5);
        reminder.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);

        cr.insert(CalendarContract.Reminders.CONTENT_URI, reminder);

        return true;
    }
    private long getGoogleCalendarId() {
        ContentResolver cr = getContentResolver();

        String[] projection = new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.SYNC_EVENTS
        };

        Cursor cur = cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                null,
                null,
                null
        );

        if (cur == null)
            return -1;

        long bestId = -1;

        while (cur.moveToNext()) {
            long id = cur.getLong(0);
            String name = cur.getString(1);
            String account = cur.getString(2);
            String type = cur.getString(3);
            String owner = cur.getString(4);
            int access = cur.getInt(5);
            int sync = cur.getInt(6);

            // Must be Google
            if (!"com.google".equals(type))
                continue;

            // Must be synced
            if (sync != 1)
                continue;

            // Must be writable
            if (access < CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR)
                continue;

            // Skip holidays/birthdays
            if (name != null && name.toLowerCase().contains("holiday"))
                continue;
            if (name != null && name.toLowerCase().contains("birthday"))
                continue;

            // Prefer primary
            if (account != null && account.equals(owner)) {
                bestId = id;
                break;
            }

            // Fallback
            if (bestId == -1)
                bestId = id;
        }

        cur.close();
        return bestId;
    }


    private long toLocalMillis(long utcMillis) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ZoneId zone = ZoneId.systemDefault();
            Instant instant = Instant.ofEpochMilli(utcMillis);
            ZonedDateTime local = instant.atZone(zone);

            return local.toInstant().toEpochMilli();
        }
        return 0;
    }
    private void scheduleAlarm(long candleLightingMillis, String event, Date diedDate) throws JSONException {

        // Build base payload
        JSONObject payload = new JSONObject();

        // Compute entry_id (stable, deterministic)
        int entryId;
        if ("shabbat".equals(event)) {
            entryId = SHABBAT_ALARM;
        } else if ("yahrzeit".equals(event)) {
            entryId = diedDate.hashCode(); // stable per person
        } else {
            showMessage("Unknown alarm type: " + event, false);
            return;
        }
        payload.put("entry_id", entryId);
        payload.put("event_type", "alarm");
        payload.put("next_candle_time", candleLightingMillis);
        payload.put("notification_request_code", 0);

        // Convert to JSON
        String json = payload.toString();

        // Send broadcast to the appropriate receiver
        Intent intent;
        if ("shabbat".equals(event)) {
            intent = new Intent(this, ShabbatAlarmReceiver.class);
        } else {
            intent = new Intent(this, YahrzeitAlarmReceiver.class);
        }
        intent.setAction("ALARM_SERVICE");
        prefs.edit().putString("ALARM_SERVICE", json).apply();
        //intent.putExtra("payload", json);

        // Trigger the receiver immediately
        sendBroadcast(intent);
    }


    // -------------------------- Stop Services ------------------------------------------------------
    public void stopAllYahrzeits() {
        cancelAllYahrzeitAlarms();
        UserSettings.clearAllYahrzeitJson(this);
        stopYahrzeitService();
        showMessage("Yahrzeit service stopped.\nUpdate calendar to restore it", true);
    }
    private void cancelAllYahrzeitAlarms()  {
        List<String> list = UserSettings.loadYahrzeitJsonList(this);
        if (list == null || list.isEmpty()) return;

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        for (String json : list) {
            try {
                JSONObject payload = new JSONObject(json);
                int entryId = payload.getInt("entry_id");

                // Cancel both offsets
                for (int offset = 1; offset <= 2; offset++) {
                    int requestCode = entryId * 10 + offset;

                    Intent intent = new Intent(this, YahrzeitAlarmReceiver.class);
                    PendingIntent pi = PendingIntent.getBroadcast(
                            this,
                            requestCode,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                    );

                    am.cancel(pi);
                }
            }
            catch (JSONException ex) {
                System.out.println("MainActivity::cancelAllYahrzeitAlarms: " + ex);
            }

        }
    }
    private void stopYahrzeitService() {
        Intent serviceIntent = new Intent(this, AlarmService.class);
        stopService(serviceIntent);
    }
    private void cancelAlarm(
            int requestCode,
            Class<?> receiverClass,
            Class<?> serviceClass
    ) {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        // Cancel the scheduled alarm
        Intent intent = new Intent(this, receiverClass);

        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        am.cancel(pi);

        // Stop the service if it's running
        Intent serviceIntent = new Intent(this, serviceClass);
        stopService(serviceIntent);
    }

    private boolean canScheduleExactAlarms() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return am.canScheduleExactAlarms();
        }

        return true; // pre-Android 12 always allowed
    }

    private boolean eventAlreadyExists(long calendarId, long startUtc, String title) {
        ContentResolver cr = getContentResolver();

        long minuteStart = (startUtc / 60000L) * 60000L;
        long minuteEnd = minuteStart + 59999L;

        String selection =
                //CalendarContract.Events.CALENDAR_ID + "=? AND " +
                CalendarContract.Events.TITLE + "=? AND " +
                CalendarContract.Events.DTSTART + ">=? AND " +
                CalendarContract.Events.DTSTART + "<=?";

        String[] selectionArgs = new String[]{
                //Long.toString(calendarId),
                title,
                Long.toString(minuteStart),
                Long.toString(minuteEnd)
        };

/*
        String selection = CalendarContract.Events.TITLE + " = ?";
        String[] selectionArgs = new String[]{ title };
*/
        Cursor cur = cr.query(
                CalendarContract.Events.CONTENT_URI,
                new String[]{CalendarContract.Events._ID},
                selection,
                selectionArgs,
                null
        );

        boolean exists = (cur != null && cur.moveToFirst());
        if (cur != null) cur.close();

        return exists;
    }
    private void updateHebrewAndInYear(YahrzeitEntry entry) {
        if (entry.diedDate == null) {
            entry.hebrewDate = "";
            entry.inYear = null;
            return;
        }

        // Convert civil date → Hebrew date
        Calendar cal = Calendar.getInstance();
        cal.setTime(entry.diedDate);

        JewishCalendar jc = new JewishCalendar();
        jc.setDate(cal);

        // Hebrew date string (full Hebrew format)
        HebrewDateFormatter formatter = new HebrewDateFormatter();
        formatter.setHebrewFormat(true);
        entry.hebrewDate = formatter.format(jc);

        // Extract Hebrew day/month/year
        int hDay = jc.getJewishDayOfMonth();
        int hMonth = jc.getJewishMonth();

        JewishCalendar jc2 = new JewishCalendar();
        int hYear = jc2.getJewishYear();
        if (hMonth == JewishCalendar.ADAR_II && !jc2.isJewishLeapYear()) {
            hMonth = JewishCalendar.ADAR;
        }
        JewishCalendar target = new JewishCalendar();
        target.setJewishDate(hYear, hMonth, hDay);

        entry.inYear = target.getGregorianCalendar().getTime();
    }
    private void openRingtonePicker() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Ringtone");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);

        startActivityForResult(intent, RINGTONE_REQUEST_CODE);
    }
    private void refreshYahrzeitList() {
        List<YahrzeitEntry> list = UserSettings.loadYahrzeitList(this);
        yahrzeitAdapter.setEntries(list);
        yahrzeitAdapter.notifyDataSetChanged();
    }
    private void scheduleDebugAlarm() {
        long now = System.currentTimeMillis();
        // Fake candle-lighting time = now + 10 seconds + 5 minutes
        long fakeCandleTime = now + 20 * 1000;
        try {
            scheduleAlarm(fakeCandleTime, "shabbat", null);
            showMessage("Debug alarm scheduled for 2 min from now", false);
        } catch (JSONException ex) {
            System.out.println("MainActivity::scheduleDebugAlarm: " + ex);
        }
    }
    private void requestLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{ Manifest.permission.ACCESS_FINE_LOCATION },
                    REQ_LOCATION
            );
            return;
        }

        // Permission already granted
        getLocationNow();
    }
    private void getLocationNow() {
        LocationHelper.getAccurateLocation(this, new LocationHelper.LocationListener() {
            @Override
            public void onLocationAvailable(double latitude, double longitude) {
                UserSettings.setLatitude(MainActivity.this, latitude);
                UserSettings.setLongitude(MainActivity.this, longitude);

                // NOW continue the Shabbat flow
                if (setCalendarPerms()) {
                    if (pendingAction == PendingAction.ADD_SHABBAT_EVENTS) {
                        eventManager.scheduleIfNeeded();
                    } else if (pendingAction == PendingAction.UPDATE_CALENDAR) {
                        updateCalendar();
                        if(UserSettings.isShabbatAlarmEnabled(MainActivity.this))
                            eventManager.scheduleIfNeeded();
                    }
                }
            }
            @Override
            public void onLocationUnavailable() {
                System.out.println("getLocationNow: location unavailable");
            }
        });
    }
}