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
import com.google.android.gms.location.Priority;

import java.util.concurrent.atomic.AtomicBoolean;

public class LocationHelper {

    public interface LocationListener {
        void onLocationAvailable(double latitude, double longitude);
        void onLocationUnavailable();
    }

    private static final float MAX_ACCEPTABLE_ACCURACY = 50f; // meters
    private static final long MAX_WAIT_TIME_MS = 30000; // 30 seconds max wait

    public static void getAccurateLocation(
            @NonNull Activity activity,
            @NonNull LocationListener listener
    ) {
        FusedLocationProviderClient fused = LocationServices.getFusedLocationProviderClient(activity);
        LocationManager lm = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);

        AtomicBoolean locationFound = new AtomicBoolean(false);
        Handler timeoutHandler = new Handler(Looper.getMainLooper());

        // 1️⃣ Permission check — NEVER silently return
        boolean fine = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean coarse = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fine && !coarse) {
            listener.onLocationUnavailable();
            return;
        }

        // 2️⃣ Timeout fallback
        timeoutHandler.postDelayed(() -> {
            if (!locationFound.get()) {
                listener.onLocationUnavailable();
            }
        }, MAX_WAIT_TIME_MS);

        // 3️⃣ Fused Location Request
        LocationRequest request =
                new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                        .setMinUpdateIntervalMillis(500)
                        .setMaxUpdates(20)
                        .build();

        LocationCallback fusedCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                for (Location loc : result.getLocations()) {
                    if (loc == null) continue;

                    double lat = loc.getLatitude();
                    double lng = loc.getLongitude();

                    float accuracy = loc.getAccuracy();
                    long ageMs = System.currentTimeMillis() - loc.getTime();

                    if (!isFallbackLocation(lat, lng)
                            && accuracy <= MAX_ACCEPTABLE_ACCURACY
                            && ageMs < 60000) {

                        if (locationFound.compareAndSet(false, true)) {
                            fused.removeLocationUpdates(this);
                            listener.onLocationAvailable(lat, lng);
                            return;
                        }
                    }
                }
            }
        };

        fused.requestLocationUpdates(request, fusedCallback, Looper.getMainLooper());

        // 4️⃣ GPS fallback
        if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000,
                    0,
                    new android.location.LocationListener() {
                        @Override
                        public void onLocationChanged(@NonNull Location loc) {
                            double lat = loc.getLatitude();
                            double lng = loc.getLongitude();
                            float accuracy = loc.getAccuracy();
                            long ageMs = System.currentTimeMillis() - loc.getTime();

                            if (!isFallbackLocation(lat, lng)
                                    && accuracy <= MAX_ACCEPTABLE_ACCURACY
                                    && ageMs < 60000) {

                                if (locationFound.compareAndSet(false, true)) {
                                    lm.removeUpdates(this);
                                    fused.removeLocationUpdates(fusedCallback);
                                    listener.onLocationAvailable(lat, lng);
                                }
                            }
                        }

                        @Override public void onProviderEnabled(@NonNull String provider) {}
                        @Override public void onProviderDisabled(@NonNull String provider) {}
                        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                    }
            );
        }
    }


    private static boolean isFallbackLocation(double lat, double lng) {
        return (lat == 0 && lng == 0)
                || (lat < -90 || lat > 90)
                || (lng < -180 || lng > 180);
    }
}
