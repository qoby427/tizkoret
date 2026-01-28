package com.emhillstudio.tizcoret;

import android.os.Bundle;
import android.text.Html;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.text.HtmlCompat;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.Objects;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        String versionName = BuildConfig.VERSION_NAME;
        String about = getString(R.string.about_text, versionName);

        TextView tv = findViewById(R.id.aboutText);
        tv.setText(HtmlCompat.fromHtml(about, HtmlCompat.FROM_HTML_MODE_LEGACY));
    }
}