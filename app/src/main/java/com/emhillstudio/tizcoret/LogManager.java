package com.emhillstudio.tizcoret;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

public class LogManager {
    private static File logFile;
    public static void init(Context context) {
        File dir = context.getExternalFilesDir("logs");
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
        logFile = new File(dir, "app_log.txt");
    }

    public static void log(String message) {
        try {
            String line = UserSettings.getLogTime(System.currentTimeMillis()) + ": " + message + "\n";
            FileOutputStream fos = new FileOutputStream(logFile, true);
            fos.write(line.getBytes());
            fos.close();
        } catch (Exception e) {
            Log.e("Tizcoret Debug", "LogManager::log: " + e.toString());
        }
    }
    public static void clearLog() {
        try {
            // Opening FileOutputStream WITHOUT append ("false") truncates the file
            FileOutputStream fos = new FileOutputStream(logFile, false);
            fos.write(new byte[0]); // optional, just ensures empty content
            fos.close();
        } catch (Exception e) {
            UserSettings.log("LogManager::clearLog: " + e.toString());
        }
    }
}

