package com.emhillstudio.tizcoret;

import static android.view.View.INVISIBLE;

import androidx.activity.EdgeToEdge;
import androidx.activity.SystemBarStyle;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import javax.mail.Session;

import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

import com.kosherjava.zmanim.ZmanimCalendar;
import com.kosherjava.zmanim.util.GeoLocation;

import org.json.JSONException;
import org.json.JSONObject;

import javax.mail.PasswordAuthentication;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;

import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.Message;

@SuppressLint("MissingPermission")
public class MainActivity extends MessageActivity {
    private TextView shabbatStatus;
    private MaterialButton shabbatToggle;
    private RecyclerView yahrzeitList;
    private YahrzeitAdapter yahrzeitAdapter;
    private String timeZoneId;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);
        prefs.edit().putLong("debug_last_candle", 0).apply();
        UserSettings.setPrefs(this, prefs);

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            UserSettings.log("FATAL: " + throwable);
            for (StackTraceElement el : throwable.getStackTrace()) {
                UserSettings.log("  at " + el.toString());
            }
        });

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

        yahrzeitAdapter = new YahrzeitAdapter(this);
        yahrzeitAdapter.setEntries(UserSettings.loadYahrzeitList(this));

        yahrzeitAdapter.setOnEntryChangedListener(entry -> {
            updateCalendarButton.setEnabled(!yahrzeitAdapter.getEntries().isEmpty());
            UserSettings.saveYahrzeitList(this, yahrzeitAdapter.getEntries());
        });

        yahrzeitList.setAdapter(yahrzeitAdapter);

        findViewById(R.id.addYahrzeitButton).setOnClickListener(v -> {
            yahrzeitAdapter.addEmptyRow();
        });
        findViewById(R.id.cancelYahrzeitButton).setVisibility(INVISIBLE);
        findViewById(R.id.cancelYahrzeitButton).setOnClickListener(v -> {
            EventManager.getInstance().cancelAllYahrzeitEvents();
        });

        if (!prefs.contains("date_format")) {
            prefs.edit().putString("date_format", "MM/dd/yyyy").apply();
        }

        updateCalendarButton.setEnabled(!yahrzeitAdapter.getEntries().isEmpty());
        updateCalendarButton.setOnClickListener(v -> {
            pendingAction = PendingAction.UPDATE_CALENDAR;
            yahrzeitAdapter.setEntries();
            if (hasLocationPermission() && hasCalendarPermission()) {
                updateCalendar();
            }
        });

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
        UserSettings.saveYahrzeitList(this, yahrzeitAdapter.getEntries());
        EventManager.getInstance().setEntries(yahrzeitAdapter.getEntries());
        EventManager.getInstance().scheduleImmediately();

        showMessage("Calendar updated", true);
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
    protected void onResume() {
        super.onResume();

        if (!hasCalendarPermission()) {
            calendarPermissionLauncher.launch(new String[]{
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
            });
            return;
        }

        if (!hasLocationPermission()) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
            return;
        }

        EventManager.init(this);
    }

    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    UserSettings.log("MainActivity::locationPermissionLauncher - permissions granted");
                    getLocationNow();
                }
            });

    private final ActivityResultLauncher<String[]> calendarPermissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {

            Boolean read = result.getOrDefault(Manifest.permission.READ_CALENDAR, false);
            Boolean write = result.getOrDefault(Manifest.permission.WRITE_CALENDAR, false);

            if (read && write) {
                UserSettings.log("MainActivity::calendarPermissionLauncher - permissions granted");
                EventManager.init(this);
            } else {
                UserSettings.log("MainActivity::calendarPermissionLauncher - permissions denied");
            }
        });
    // -----------------------------
    //  Shabbat calendar insertion
    // -----------------------------
    private void updateShabbatUI(boolean enabled) {
        String msg = "Candle lighting notifications and\nalarms are currently turned ";
        if (enabled) {
            shabbatStatus.setText(msg + "ON");
            shabbatToggle.setText("Turn Off");
        } else {
            shabbatStatus.setText(msg + "OFF");
            shabbatToggle.setText("Turn On");
        }
        UserSettings.setShabbatAlarmEnabled(this, enabled);
    }

    private void maybeAskToAddShabbat() {
        boolean enabled = UserSettings.isShabbatAlarmEnabled(this);
        UserSettings.setShabbatAlarmEnabled(this, !enabled);
        if (!enabled) {
            showQuestion("Shabbat and Yahrzeit Zmanim",
                    "Would you like to start Shabbat and Yahrzeit notifications?",
                    () -> {
                        updateShabbatUI(true);

                        pendingAction = PendingAction.ADD_SHABBAT_EVENTS;
                        if (hasLocationPermission() && hasCalendarPermission()) {
                            EventManager.getInstance().scheduleImmediately();
                            return;
                        }
             });
        }
        else {
            showQuestion("Shabbat and Yahrzeit Zmanim",
                    "Would you like to stop Shabbat and Yahrzeit notifications?",
                    () -> {
                        updateShabbatUI(false);

                        cancelAlarm(
                                REQ_LOCATION,
                                ShabbatAlarmReceiver.class,
                                AlarmService.class
                        );
                        EventManager.getInstance().cancelAll();
                        UserSettings.clearLog();
                    });
        }
    }
    // -------------------------- Stop Services ------------------------------------------------------
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
    private void getLocationNow() {
        LocationHelper.getAccurateLocation(this, new LocationHelper.LocationListener() {
            @Override
            public void onLocationAvailable(double latitude, double longitude) {
                UserSettings.setLatitude(MainActivity.this, latitude);
                UserSettings.setLongitude(MainActivity.this, longitude);

                // NOW continue the Shabbat flow
                if (setCalendarPerms()) {
                    if (pendingAction == PendingAction.ADD_SHABBAT_EVENTS) {
                        EventManager.getInstance().scheduleImmediately();
                    } else if (pendingAction == PendingAction.UPDATE_CALENDAR) {
                        updateCalendar();
                        if(UserSettings.isShabbatAlarmEnabled(MainActivity.this))
                            EventManager.getInstance().scheduleImmediately();
                    }
                }
            }
            @Override
            public void onLocationUnavailable() {
                UserSettings.log("getAccurateLocation: location unavailable");
            }
        });
    }
}