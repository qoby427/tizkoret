package com.emhillstudio.tizcoret;

import android.location.Location;
public class PassiveLocationStore {
    private static volatile Location lastPassiveLocation = null;
    public static void update(Location loc) {
        lastPassiveLocation = loc;
    }
    public static Location get() {
        return lastPassiveLocation;
    }
}
