package com.emhillstudio.tizcoret;

import androidx.core.app.ActivityCompat;
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
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Rect;
import android.location.Location;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.CalendarContract;
import android.view.View;
import android.widget.TextView;

import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.material.button.MaterialButton;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.kosherjava.zmanim.ZmanimCalendar;
import com.kosherjava.zmanim.hebrewcalendar.HebrewDateFormatter;
import com.kosherjava.zmanim.util.GeoLocation;
import com.kosherjava.zmanim.hebrewcalendar.JewishCalendar;

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
    private boolean wasKeyboardVisible = false;

    public interface LocationListener {
        void onLocationAvailable(double latitude, double longitude);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // -----------------------------
        //  UI references
        // -----------------------------
        shabbatStatus = findViewById(R.id.shabbatStatus);
        shabbatToggle = findViewById(R.id.shabbatToggleButton);
        yahrzeitList = findViewById(R.id.yahrzeitList);

        TextView headerInYear = findViewById(R.id.headerInYear);
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);
        headerInYear.setText("In " + currentYear);

        LocationHelper.getAccurateLocation(this, new LocationHelper.LocationListener() {
            @Override
            public void onLocationAvailable(double latitude, double longitude) {
                UserSettings.setLatitude(MainActivity.this, latitude);
                UserSettings.setLongitude(MainActivity.this, longitude);
            }

            @Override
            public void onLocationUnavailable() {
            }
        });

        // -----------------------------
        //  Shabbat toggle
        // -----------------------------
        boolean enabled = UserSettings.isShabbatAlarmEnabled(this);
        updateShabbatUI(enabled);

        shabbatToggle.setOnClickListener(v -> {
            maybeAskToAddShabbat();
        });

        // -----------------------------
        //  Yahrzeit table
        // -----------------------------
        yahrzeitList = findViewById(R.id.yahrzeitList);
        yahrzeitList.setLayoutManager(new LinearLayoutManager(this));
        yahrzeitAdapter = new YahrzeitAdapter(this, list, entry -> {
            updateHebrewAndInYear(entry);
            yahrzeitAdapter.notifyDataSetChanged();
        });
        yahrzeitList.setAdapter(yahrzeitAdapter);

        List<YahrzeitEntry> saved = UserSettings.loadYahrzeitList(this);
        yahrzeitAdapter.setEntries(saved);

        findViewById(R.id.addYahrzeitButton).setOnClickListener(v -> {
            yahrzeitAdapter.addEmptyRow();
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
                    2001
            );
        }

        timeZoneId = TimeZone.getDefault().getID();

        ShabbatWorker.schedule(this);

        if (BuildConfig.DEBUG) {
            //UserSettings.setShabbatAlarmEnabled(this, false);
            //updateShabbatUI(false);
            //scheduleDebugAlarm();
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
    //  GPS
    // -----------------------------
    private void requestGpsCoordinates() {
        FusedLocationProviderClient fused = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1001
            );
            return;
        }

        LocationRequest request = LocationRequest.create();
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        request.setNumUpdates(1);
        request.setInterval(1000);

        fused.requestLocationUpdates(
                request,
                new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult result) {
                        Location loc = result.getLastLocation();
                        if (loc != null) {
                            UserSettings.setLatitude(
                                    MainActivity.this,
                                    loc.getLatitude()
                            );
                            UserSettings.setLongitude(
                                    MainActivity.this,
                                    loc.getLongitude()
                            );
                        }
                        fused.removeLocationUpdates(this);
                    }
                },
                Looper.getMainLooper()
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == 1001 && grantResults.length > 0 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            requestGpsCoordinates();
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
                        addNextFridayShabbatEvents();
                    });
        }
        else {
            showQuestion("Add Shabbat Times",
                    "Would you like to remove Shabbat alarm?",
                    () -> {
                        cancelShabbatAlarm();
                    });
        }
    }

    private LocalDate getNextFriday() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate today = LocalDate.now();
            return today.with(TemporalAdjusters.next(DayOfWeek.FRIDAY));
        }
        return null;
    }

    private void requestAccurateLocation(LocationListener callback) {
        FusedLocationProviderClient fused =
                LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    1001
            );
            return;
        }

        LocationRequest request = LocationRequest.create();
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        request.setInterval(1000);
        request.setSmallestDisplacement(1); // meters
        request.setNumUpdates(5); // wait for several fixes

        fused.requestLocationUpdates(
                request,
                new LocationCallback() {
                    @Override
                    public void onLocationResult(LocationResult result) {
                        for (Location loc : result.getLocations()) {
                            if (loc != null && loc.getAccuracy() < 100) {
                                // Valid fix
                                callback.onLocationAvailable(loc.getLatitude(), loc.getLongitude());
                                fused.removeLocationUpdates(this);
                                break;
                            }
                        }
                    }
                },
                Looper.getMainLooper()
        );
    }

    private void saveAccurateLocation(LocationListener listener) {
        FusedLocationProviderClient fused = LocationServices.getFusedLocationProviderClient(this);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            return;
        }

        LocationRequest request = LocationRequest.create();
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        request.setInterval(1000);
        request.setNumUpdates(10); // request multiple updates to allow GPS to lock

        fused.requestLocationUpdates(request, new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                for (Location loc : result.getLocations()) {
                    if (loc != null && loc.getAccuracy() < 50) { // meters
                        double lat = loc.getLatitude();
                        double lng = loc.getLongitude();
                        System.out.println("Latitude: " + lat + ", Longitude: " + lng);

                        // Ignore fallback (CA) coordinates
                        if (!(lat > 37 && lat < 38 && lng > -123 && lng < -122)) {
                            UserSettings.setLatitude(MainActivity.this, lat);
                            UserSettings.setLongitude(MainActivity.this, lng);
                            listener.onLocationAvailable(lat, lng);
                            fused.removeLocationUpdates(this);
                            return;
                        }
                    }
                }
            }
        }, Looper.getMainLooper());
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


    void addNextFridayShabbatEvents() {
        if (!BuildConfig.DEBUG) return;

        LocalDate friday = getNextFriday();
        if (friday == null) return;

        ZmanimCalendar zc = buildZmanimCalendar();

        Calendar cal = Calendar.getInstance();
        cal.setTimeZone(TimeZone.getDefault());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            cal.set(friday.getYear(), friday.getMonthValue() - 1, friday.getDayOfMonth());
        }
        zc.setCalendar(cal);

        Date candleLighting = zc.getCandleLighting();
        if (candleLighting == null) return;

        long candleLightingMillis = candleLighting.getTime();

        insertShabbatEvent(candleLightingMillis);
        scheduleShabbatAlarm(candleLightingMillis);
    }

    private void insertShabbatEvent(long candleLightingMillis) {
        long calendarId = getGoogleCalendarId();
        if (calendarId == -1)
            return;

        ContentResolver cr = getContentResolver();


        // Format time for title
        String formatted = new SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(new Date(candleLightingMillis));
        String title = "Candle Lighting – " + formatted;

        if (eventAlreadyExists(calendarId, candleLightingMillis, title))
            return;

        // 1. Insert event into EVENTS table
        ContentValues event = new ContentValues();

        event.put(CalendarContract.Events.TITLE, title);
        event.put(CalendarContract.Events.CALENDAR_ID, calendarId);
        event.put(CalendarContract.Events.EVENT_TIMEZONE, timeZoneId);
        event.put(CalendarContract.Events.EVENT_END_TIMEZONE, timeZoneId);
        event.put(CalendarContract.Events.DTSTART, candleLightingMillis);
        event.put(CalendarContract.Events.DTEND, candleLightingMillis + 60 * 60 * 1000);

        Uri eventUri = cr.insert(CalendarContract.Events.CONTENT_URI, event);
        if (eventUri == null)
            return;

        long eventId = Long.parseLong(eventUri.getLastPathSegment());

        // 2. Add your 18-minute reminder
        ContentValues reminder = new ContentValues();
        reminder.put(CalendarContract.Reminders.EVENT_ID, eventId);
        reminder.put(CalendarContract.Reminders.MINUTES, 18);
        reminder.put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);

        cr.insert(CalendarContract.Reminders.CONTENT_URI, reminder);
    }

    private long getGoogleCalendarId() {
        ContentResolver cr = getContentResolver();

        String[] projection = new String[]{
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE,
                CalendarContract.Calendars.OWNER_ACCOUNT,
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL
        };

        // We only want:
        // - Google calendars (ACCOUNT_TYPE = com.google)
        // - Writable calendars (ACCESS_LEVEL >= CONTRIBUTOR)
        String selection =
                CalendarContract.Calendars.ACCOUNT_TYPE + "=? AND " +
                        CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL + ">=?";

        String[] selectionArgs = new String[]{
                "com.google",
                Integer.toString(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR) // 500
        };

        Cursor cur = cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
        );

        if (cur == null)
            return -1;

        long calendarId = -1;

        while (cur.moveToNext()) {
            long id = cur.getLong(0);
            String name = cur.getString(1);
            String account = cur.getString(2);
            String owner = cur.getString(4);
            int access = cur.getInt(5);

            // Skip Holidays, Birthdays, and other system calendars
            if (name != null && name.toLowerCase().contains("holiday"))
                continue;
            if (name != null && name.toLowerCase().contains("birthday"))
                continue;

            // Prefer calendars owned by the user
            if (account != null && account.equals(owner)) {
                calendarId = id;
                break;
            }

            // Fallback: first writable Google calendar
            if (calendarId == -1)
                calendarId = id;
        }

        cur.close();
        return calendarId;
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

    private void scheduleShabbatAlarm(long candleLightingMillis) {
        long localCandle = toLocalMillis(candleLightingMillis);
        long triggerAt = localCandle - 18 * 60 * 1000;

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (!canScheduleExactAlarms()) {
            showMessage("Exact alarms are disabled. Please allow them in system settings.", false);
            return;
        }

        Intent intent = new Intent(this, ShabbatAlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
        } catch (SecurityException e) {
            showMessage("Cannot schedule exact alarms. Enable permission in system settings.", false);
        }
    }

    private void cancelShabbatAlarm() {
        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(this, ShabbatAlarmReceiver.class);

        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                1001,  // must match the one used when scheduling
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        am.cancel(pi);  // cancels the alarm

        // Also stop the service if it's running
        Intent serviceIntent = new Intent(this, ShabbatAlarmService.class);
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
                CalendarContract.Events.CALENDAR_ID + "=? AND " +
                        CalendarContract.Events.TITLE + "=? AND " +
                        CalendarContract.Events.DTSTART + ">=? AND " +
                        CalendarContract.Events.DTSTART + "<=?";

        String[] selectionArgs = new String[]{
                Long.toString(calendarId),
                title,
                Long.toString(minuteStart),
                Long.toString(minuteEnd)
        };

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

    private void scheduleDebugAlarm() {
        long triggerAt = System.currentTimeMillis() + 0 * 1000;

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
            showMessage("Exact alarms disabled in system settings", false);
            return;
        }

        Intent intent = new Intent(this, ShabbatAlarmReceiver.class);

        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                9999, // debug alarm ID
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }

        showMessage("Debug alarm scheduled for 10 sec from now", false);
    }

    private void updateHebrewAndInYear(YahrzeitEntry entry) {
        if (entry.diedDate == null) {
            entry.hebrewDate = "";
            entry.inYear = "";
            yahrzeitAdapter.notifyDataSetChanged();
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

        // Compute Gregorian date of this Hebrew date in the current civil year
        int currentYear = Calendar.getInstance().get(Calendar.YEAR);

        JewishCalendar jc2 = new JewishCalendar();
        jc2.setJewishDate(currentYear, hMonth, hDay);

        Date greg = jc2.getGregorianCalendar().getTime();
        entry.inYear = new SimpleDateFormat("MMM d", Locale.getDefault()).format(greg);

        yahrzeitAdapter.notifyDataSetChanged();
    }
    private void setupKeyboardListener(View root) {
        root.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            Rect r = new Rect();
            root.getWindowVisibleDisplayFrame(r);

            int screenHeight = root.getRootView().getHeight();
            int keypadHeight = screenHeight - r.bottom;

            boolean isKeyboardNowVisible = keypadHeight > screenHeight * 0.15;

            if (!isKeyboardNowVisible && wasKeyboardVisible) {
                onKeyboardHidden();
            }

            wasKeyboardVisible = isKeyboardNowVisible;
        });
    }
    private void onKeyboardHidden() {
        for (YahrzeitEntry entry : yahrzeitAdapter.getEntries()) {
            if (entry.diedDate != null) {
                entry.hebrewDate = HebrewUtils.toHebrewDate(entry.diedDate);
                entry.inYear = HebrewUtils.computeInYear(entry.diedDate);
            }
        }

        UserSettings.saveYahrzeitList(this, yahrzeitAdapter.getEntries());
        yahrzeitAdapter.notifyDataSetChanged();
    }
}