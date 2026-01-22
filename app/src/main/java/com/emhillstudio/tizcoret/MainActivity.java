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
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Location;
import android.media.RingtoneManager;
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

import java.text.ParseException;
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

    @Override
    protected void onResume() {
        super.onResume();
        refreshYahrzeitList();
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

        MaterialButton updateCalendarButton = findViewById(R.id.updateCalendarButton);

        if (saved.isEmpty()) {
            updateCalendarButton.setVisibility(View.GONE);
        } else {
            updateCalendarButton.setVisibility(View.VISIBLE);
        }

        findViewById(R.id.addYahrzeitButton).setOnClickListener(v -> {
            yahrzeitAdapter.addEmptyRow();
        });

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);

        if (!prefs.contains("date_format")) {
            prefs.edit().putString("date_format", "MM/dd/yyyy").apply();
        }

        updateCalendarButton.setOnClickListener(v -> {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd yyyy", Locale.US);

            for (YahrzeitEntry entry : yahrzeitAdapter.getEntries()) {
                if(entry.name.isEmpty() || entry.diedDate.toString().isEmpty())
                    continue;
                String yahrzeit = entry.inYear + " " + currentYear;
                try {
                    Date yahrzeitDate = sdf.parse(yahrzeit);
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(yahrzeitDate);
                    cal.add(Calendar.DAY_OF_MONTH, -1);

                    ZmanimCalendar zc = buildZmanimCalendar();
                    zc.setCalendar(cal);

                    Date candleLighting = zc.getSunset();
                    String msg = entry.name.strip()+"'s Yahrzeit tomorrow. ";
                    if(insertCalendarEvent(candleLighting.getTime(), msg)) {
                        scheduleAlarm(candleLighting.getTime(), "yahrzeit",entry.diedDate);
                    }
                } catch (ParseException e) {
                    showMessage("Cannot pars date "+yahrzeit+"\n"+e.toString(), false);
                    return;
                }
            }
            showMessage("Calendar updated", true);
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

        findViewById(R.id.buttonAbout).setOnClickListener(v -> {
            startActivity(new Intent(this, AboutActivity.class));
        });

        findViewById(R.id.buttonCustomize).setOnClickListener(v -> {
            startActivity(new Intent(this, CustomizeActivity.class));
        });

        findViewById(R.id.buttonSupport).setOnClickListener(v -> {
            startActivity(new Intent(this, SupportActivity.class));
        });

        if (BuildConfig.DEBUG) {
            //UserSettings.setShabbatAlarmEnabled(this, false);
            //updateShabbatUI(false);
            //scheduleDebugAlarm();
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RINGTONE_REQUEST_CODE && resultCode == RESULT_OK) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

            if (uri != null) {
                getSharedPreferences("prefs", MODE_PRIVATE)
                        .edit()
                        .putString("alarm_ringtone", uri.toString())
                        .apply();
            }
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
                        cancelAlarm(
                                1001,
                                ShabbatAlarmReceiver.class,
                                AlarmService.class
                        );
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

        if(insertCalendarEvent(candleLightingMillis,""))
            scheduleAlarm(candleLightingMillis, "shabbat", null);
    }

    private boolean insertCalendarEvent(long candleLightingMillis, String header) {
        long calendarId = getGoogleCalendarId();
        if (calendarId == -1)
            return false;

        ContentResolver cr = getContentResolver();

        // Format time for title
        String formatted = new SimpleDateFormat("h:mm a", Locale.getDefault())
                .format(new Date(candleLightingMillis));

        String title = header + "Candle Lighting – " + formatted+". Brachot from Tizcóret team";

        if (eventAlreadyExists(calendarId, candleLightingMillis, title))
            return false;

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
            return false;

        long eventId = Long.parseLong(eventUri.getLastPathSegment());

        // 2. Add your 18-minute reminder
        ContentValues reminder = new ContentValues();
        reminder.put(CalendarContract.Reminders.EVENT_ID, eventId);
        reminder.put(CalendarContract.Reminders.MINUTES, 18);
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

    private void scheduleAlarm(long candleLightingMillis, String event, Date diedDate) {
        long localCandle = toLocalMillis(candleLightingMillis);
        long triggerAt = localCandle - 18 * 60 * 1000;

        AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        if (!canScheduleExactAlarms()) {
            showMessage("Exact alarms are disabled. Please allow them in system settings.", false);
            return;
        }

        Class<?> receiverClass;
        int requestCode;

        if ("shabbat".equals(event)) {
            receiverClass = ShabbatAlarmReceiver.class;
            requestCode = 1001;
        } else if ("yahrzeit".equals(event)) {
            receiverClass = YahrzeitAlarmReceiver.class;
            requestCode = diedDate.hashCode();
        } else {
            showMessage("Unknown alarm type: " + event, false);
            return;
        }

        Intent intent = new Intent(this, receiverClass);

        SharedPreferences prefs = getSharedPreferences("prefs", MODE_PRIVATE);
        String uriString = prefs.getString("ringtone_" + event, null);
        intent.putExtra("ringtone_uri", uriString);
        if ("yahrzeit".equals(event))
            intent.putExtra("died_date", diedDate);

        PendingIntent pi = PendingIntent.getBroadcast(
                this,
                requestCode,
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

        long minuteStart = Math.round(startUtc / 60000.0) * 60000L;
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
    private static final int RINGTONE_REQUEST_CODE = 1234;

    private void openRingtonePicker() {
        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Alarm Ringtone");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);

        startActivityForResult(intent, RINGTONE_REQUEST_CODE);
    }
    private void refreshYahrzeitList() {
        // Reload your data (from DB, SharedPreferences, file, etc.)
        List<YahrzeitEntry> list = UserSettings.loadYahrzeitList(this);

        // Recreate adapter or update it
        yahrzeitAdapter.setEntries(list);
        yahrzeitAdapter.notifyDataSetChanged();
    }

}