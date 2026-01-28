package com.emhillstudio.tizcoret;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class CustomizeActivity extends AppCompatActivity {

    private static final int REQ_SHABBAT_RINGTONE = 2001;
    private static final int REQ_YAHRZEIT_RINGTONE = 2002;

    private SharedPreferences prefs;
    private TextView shText;
    private TextView yzText;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customize);

        prefs = getSharedPreferences(UserSettings.PREFS, MODE_PRIVATE);

        shText = findViewById(R.id.shabbatRingtone);
        yzText = findViewById(R.id.yahrzeitRingtone);

        findViewById(R.id.btnShabbatRingtone).setOnClickListener(v ->
                openRingtonePicker(REQ_SHABBAT_RINGTONE)
        );

        findViewById(R.id.btnYahrzeitRingtone).setOnClickListener(v ->
                openRingtonePicker(REQ_YAHRZEIT_RINGTONE)
        );
        updateLabels();
        setupDateFormatSelector();
    }

    private void openRingtonePicker(int requestCode) {
        Uri uri;
        if(requestCode == REQ_SHABBAT_RINGTONE) {
            uri = UserSettings.getShabbatRingtone(this);
        }
        else {
            uri = UserSettings.getYahrzeitRingtone(this);
        }

        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Ringtone");
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);

        // ⭐ This highlights the current ringtone
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri);

        startActivityForResult(intent, requestCode);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        if (uri == null) return;

        SharedPreferences.Editor editor = prefs.edit();

        if (requestCode == REQ_SHABBAT_RINGTONE) {
            editor.putString("ringtone_shabbat", uri.toString());
            UserSettings.setShabbatRingtone(this, uri);
        } else if (requestCode == REQ_YAHRZEIT_RINGTONE) {
            editor.putString("ringtone_yahrzeit", uri.toString());
            UserSettings.setYahrzeitRingtone(this, uri);
        }

        editor.apply();
        updateLabels();
    }

    private void setupDateFormatSelector() {
        RadioGroup group = findViewById(R.id.dateFormatGroup);
        RadioButton mmdd = findViewById(R.id.format_mmdd);
        RadioButton ddmm = findViewById(R.id.format_ddmm);

        String saved = prefs.getString("date_format", "MM/dd/yyyy");

        if (saved.equals("MM/dd/yyyy")) {
            mmdd.setChecked(true);
        } else {
            ddmm.setChecked(true);
        }

        group.setOnCheckedChangeListener((g, checkedId) -> {
            String format = checkedId == R.id.format_mmdd
                    ? "MM/dd/yyyy"
                    : "dd/MM/yyyy";

            prefs.edit().putString("date_format", format).apply();
        });
    }
    private void updateLabels() {
        Uri shUri = UserSettings.getShabbatRingtone(this);
        Uri yzUri = UserSettings.getYahrzeitRingtone(this);

        shText.setText("Shabbat Ringtone: " + getRingtoneName(shUri));
        yzText.setText("Yahrzeit Ringtone: " + getRingtoneName(yzUri));
    }

    private String getRingtoneName(Uri uri) {
        if (uri == null) return "Default";
        try {
            return RingtoneManager.getRingtone(this, uri).getTitle(this);
        } catch (Exception e) {
            return "Default";
        }
    }
}
