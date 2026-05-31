package com.emhillstudio.tizcoret;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.location.Location;

public class PassiveLocationStore {

    @SuppressLint("ObsoleteSdkInt")
    public static void update(Context context, Location loc) {
        try {
            UserSettings.setLongitude(context, loc.getLongitude());
            UserSettings.setLatitude(context, loc.getLatitude());
            UserSettings.setLocationTime(context, loc.getTime());
        } catch (Exception e) {
            UserSettings.log("PassiveLocationStore::update - " + e);
        }
    }
    public static Location get(Context context) {
        try {
            double lat = UserSettings.getLatitude(context);
            double lon = UserSettings.getLongitude(context);
            long time = UserSettings.getLocationTime(context);

            if (lat == 0 || lon == 0 || time == 0L)
                return null;

            Location loc = new Location("passive");
            loc.setLatitude(lat);
            loc.setLongitude(lon);
            loc.setTime(time);
            return loc;
        } catch (Exception e) {
            UserSettings.log("PassiveLocationStore::get - " + e);
        }
        return null;
    }
}
