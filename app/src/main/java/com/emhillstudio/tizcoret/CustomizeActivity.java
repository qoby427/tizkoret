package com.emhillstudio.tizcoret;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.button.MaterialButton;

public class CustomizeActivity extends MessageActivity {

    private static final int REQ_SHABBAT_RINGTONE = 2001;
    private static final int REQ_YAHRZEIT_RINGTONE = 2002;
    private SharedPreferences prefs;
    private TextView shText;
    private TextView yzText;
    private MaterialButton btnShabbat;
    private MaterialButton btnYahrzeit;
    private CheckBox cbShabbatDefault;
    private CheckBox cbYahrzeitDefault;
    private ActivityResultLauncher<Intent> shabbatRingtoneLauncher;
    private ActivityResultLauncher<Intent> yahrzeitRingtoneLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customize);

        prefs = getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);

        // ----------------------------------------------------
        // RINGTONE PICKER LAUNCHERS
        // ----------------------------------------------------
        shabbatRingtoneLauncher =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            uri = result.getData().getParcelableExtra(
                                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                                    Uri.class
                            );
                        }
                        else {
                            uri = result.getData().getParcelableExtra(
                                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                            );
                        }
                        if (uri != null) {
                            UserSettings.setShabbatRingtone(this, uri);
                            updateLabels();
                        }
                    }
                });

        yahrzeitRingtoneLauncher =
                registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = null;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            uri = result.getData().getParcelableExtra(
                                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                                    Uri.class
                            );
                        }
                        else {
                            uri = result.getData().getParcelableExtra(
                                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI
                            );
                        }
                        if (uri != null) {
                            UserSettings.setYahrzeitRingtone(this, uri);
                            updateLabels();
                        }
                    }
                });

        // ----------------------------------------------------
        // VIEW BINDINGS
        // ----------------------------------------------------
        shText = findViewById(R.id.shabbatRingtone);
        yzText = findViewById(R.id.yahrzeitRingtone);

        btnShabbat = findViewById(R.id.btnShabbatRingtone);
        btnYahrzeit = findViewById(R.id.btnYahrzeitRingtone);

        cbShabbatDefault = findViewById(R.id.shabbatDefaultCheckbox);
        cbShabbatDefault.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                // Use default
                prefs.edit().remove(UserSettings.SHABBAT_RINGTONE).apply();
                btnShabbat.setText("Using Default Ringtone");
                btnShabbat.setEnabled(false);
                updateLabels();
            } else {
                // Allow choosing custom
                btnShabbat.setText("Choose Shabbat Ringtone");
                btnShabbat.setEnabled(true);
            }
            prefs.edit().putBoolean("shabbat_checkbox", isChecked).apply();
        });
        cbYahrzeitDefault = findViewById(R.id.yahrzeitDefaultCheckbox);
        cbYahrzeitDefault.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                prefs.edit().remove(UserSettings.YAHRZEIT_RINGTONE).apply();
                btnYahrzeit.setText("Using Default Ringtone");
                btnYahrzeit.setEnabled(false);
                updateLabels();
            } else {
                btnYahrzeit.setText("Choose Yahrzeit Ringtone");
                btnYahrzeit.setEnabled(true);
            }
            prefs.edit().putBoolean("yahrzeit_checkbox", isChecked).apply();
        });

        cbShabbatDefault.setChecked(prefs.getBoolean("shabbat_checkbox", true));
        cbYahrzeitDefault.setChecked(prefs.getBoolean("yahrzeit_checkbox", true));

        // ----------------------------------------------------
        // BUTTON CLICK → OPEN PICKER
        // ----------------------------------------------------
        btnShabbat.setOnClickListener(v -> openRingtonePicker(REQ_SHABBAT_RINGTONE));
        btnYahrzeit.setOnClickListener(v -> openRingtonePicker(REQ_YAHRZEIT_RINGTONE));

        // ----------------------------------------------------
        // INITIAL LABELS + SETTINGS
        // ----------------------------------------------------
        updateLabels();
        setupDateFormatSelector();
        setupEarlyVolumeSelector();
    }

    // ----------------------------------------------------
    // RINGTONE PICKER
    // ----------------------------------------------------
    private void openRingtonePicker(int requestCode) {
        Uri uri = (requestCode == REQ_SHABBAT_RINGTONE)
                ? UserSettings.getShabbatRingtone(this)
                : UserSettings.getYahrzeitRingtone(this);

        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri);

        if (requestCode == REQ_SHABBAT_RINGTONE) {
            shabbatRingtoneLauncher.launch(intent);
        } else {
            yahrzeitRingtoneLauncher.launch(intent);
        }
    }

    // ----------------------------------------------------
    // DATE FORMAT
    // ----------------------------------------------------
    private void setupDateFormatSelector() {
        RadioGroup group = findViewById(R.id.dateFormatGroup);
        RadioButton mmdd = findViewById(R.id.format_mmdd);
        RadioButton ddmm = findViewById(R.id.format_ddmm);

        String saved = prefs.getString("date_format", "MM/dd/yyyy");

        if (saved.equals("MM/dd/yyyy")) mmdd.setChecked(true);
        else ddmm.setChecked(true);

        group.setOnCheckedChangeListener((g, checkedId) -> {
            String format = checkedId == R.id.format_mmdd
                    ? "MM/dd/yyyy"
                    : "dd/MM/yyyy";

            UserSettings.setDateFormat(checkedId == R.id.format_mmdd);
            prefs.edit().putString("date_format", format).apply();
        });
    }

    // ----------------------------------------------------
    // EARLY NOTIFICATION VOLUME
    // ----------------------------------------------------
    private void setupEarlyVolumeSelector() {
        RadioGroup group = findViewById(R.id.earlyVolumeGroup);
        RadioButton loud = findViewById(R.id.earlyVolumeLoud);
        RadioButton quiet = findViewById(R.id.earlyVolumeQuiet);

        boolean isLoud = UserSettings.isEarlyLoud(this);

        if (isLoud) loud.setChecked(true);
        else quiet.setChecked(true);

        group.setOnCheckedChangeListener((g, checkedId) -> {
            boolean loudSelected = checkedId == R.id.earlyVolumeLoud;
            UserSettings.setEarlyLoud(this, loudSelected);
        });
    }

    // ----------------------------------------------------
    // LABELS
    // ----------------------------------------------------
    private boolean isRawResource(Uri uri) {
        return uri != null && "android.resource".equals(uri.getScheme());
    }
    private String getRingtoneName(Context context, Uri uri) {
        if (uri == null) return "Default";

        if (isRawResource(uri)) {
            int resId = Integer.parseInt(uri.getLastPathSegment());
            if (resId == R.raw.lecha_dodi) return "Lecha Dodi";
            if (resId == R.raw.schumann_fantasy) return "Yahrzeit Default";
        }

        Ringtone r = RingtoneManager.getRingtone(context, uri);
        return r != null ? r.getTitle(context) : "Unknown";
    }
    private void updateLabels() {
        Uri shUri = UserSettings.getShabbatRingtone(this);
        Uri yzUri = UserSettings.getYahrzeitRingtone(this);

        shText.setText("Shabbat Ringtone: " + getRingtoneName(this, shUri));
        yzText.setText("Yahrzeit Ringtone: " + getRingtoneName(this, yzUri));
    }
}
