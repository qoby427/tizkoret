package com.emhillstudio.tizcoret;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

import java.util.concurrent.atomic.AtomicBoolean;

public class LocationHelper {

    public interface LocationListener {
        void onLocationAvailable(double latitude, double longitude);
        void onLocationUnavailable();
    }

    private static final float MAX_ACCEPTABLE_ACCURACY = 50f; // meters
    private static final long MAX_WAIT_TIME_MS = 30000; // 30 seconds max wait

    /**
     * Requests a high-accuracy location like professional apps.
     */
    public static void getAccurateLocation(@NonNull Activity activity,
                                           @NonNull LocationListener listener) {

        FusedLocationProviderClient fused = LocationServices.getFusedLocationProviderClient(activity);
        LocationManager lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        AtomicBoolean locationFound = new AtomicBoolean(false);
        Handler timeoutHandler = new Handler(Looper.getMainLooper());

        // Check permission
        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 1001);
            listener.onLocationUnavailable();
            return;
        }

        // Timeout: if no location after MAX_WAIT_TIME_MS, give up
        timeoutHandler.postDelayed(() -> {
            if (!locationFound.get()) {
                listener.onLocationUnavailable();
            }
        }, MAX_WAIT_TIME_MS);

        // 1️⃣ Request Fused updates
        LocationRequest request = LocationRequest.create();
        request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        request.setInterval(1000);   // 1 sec updates
        request.setFastestInterval(500);
        request.setNumUpdates(20);   // allow multiple updates

        LocationCallback fusedCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                for (Location loc : result.getLocations()) {
                    if (loc == null) continue;

                    double lat = loc.getLatitude();
                    double lng = loc.getLongitude();
                    float accuracy = loc.getAccuracy();
                    long ageMs = System.currentTimeMillis() - loc.getTime();

                    // Debug
                    System.out.printf("Fused Lat: %.5f, Lng: %.5f, Acc: %.1f, Age: %dms\n",
                            lat, lng, accuracy, ageMs);

                    if (!isFallbackLocation(lat, lng) && accuracy <= MAX_ACCEPTABLE_ACCURACY && ageMs < 60000) {
                        if (locationFound.compareAndSet(false, true)) {
                            UserSettings.setLatitude(activity, lat);
                            UserSettings.setLongitude(activity, lng);
                            fused.removeLocationUpdates(this);
                            listener.onLocationAvailable(lat, lng);
                            return;
                        }
                    }
                }
            }
        };
        fused.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper());

        // 2️⃣ GPS fallback
        if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0,
                    new android.location.LocationListener() {
                        @Override
                        public void onLocationChanged(@NonNull Location loc) {
                            double lat = loc.getLatitude();
                            double lng = loc.getLongitude();
                            float accuracy = loc.getAccuracy();
                            long ageMs = System.currentTimeMillis() - loc.getTime();

                            System.out.printf("GPS Lat: %.5f, Lng: %.5f, Acc: %.1f, Age: %dms\n",
                                    lat, lng, accuracy, ageMs);

                            if (!isFallbackLocation(lat, lng) && accuracy <= MAX_ACCEPTABLE_ACCURACY && ageMs < 60000) {
                                if (locationFound.compareAndSet(false, true)) {
                                    UserSettings.setLatitude(activity, lat);
                                    UserSettings.setLongitude(activity, lng);
                                    lm.removeUpdates(this);
                                    fused.removeLocationUpdates(fusedCallback);
                                    listener.onLocationAvailable(lat, lng);
                                }
                            }
                        }

                        @Override public void onProviderEnabled(@NonNull String provider) {}
                        @Override public void onProviderDisabled(@NonNull String provider) {}
                        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    });
        }
    }

    /**
     * Reject obviously invalid fallback coordinates
     */
    private static boolean isFallbackLocation(double lat, double lng) {
        return (lat == 0 && lng == 0)
                || (lat < -90 || lat > 90)
                || (lng < -180 || lng > 180);
    }
}
