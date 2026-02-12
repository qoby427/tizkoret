package com.emhillstudio.tizcoret;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;

public class YahrzeitAlarmReceiver extends AlarmReceiver {
    @Override
    protected String channel(){
        return "yahrzeit";
    }
    @Override
    protected int icon(){
        return R.drawable.ic_yahrzeit_candle;
    }
}
