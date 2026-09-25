package com.mvx.agriculture.data;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.mvx.agriculture.CityRepository;
import com.mvx.agriculture.User;

import java.util.List;

/**
 * Assembles what {@link WeatherLocations} needs from Android, so the resolver
 * itself can stay free of Context and remain unit-testable.
 */
public class WeatherLocationProvider {

    private static final String TAG = "WeatherLocationProvider";

    // A last-known fix this old is treated the same as no fix at all: on a phone
    // where nothing has asked for location in a while, a leftover passive/network
    // fix can be hours or days old and would otherwise be shown as "current".
    private static final long MAX_FIX_AGE_MS = 30 * 60 * 1000L;

    private final Context context;
    private final WeatherLocationStore store;

    // Lazily built: CityRepository parses the whole cities.csv in its
    // constructor, and we must not pay that cost more than once per provider.
    private CityRepository cityRepository;

    // Kept only while a single-fix refresh (requestFreshFix) is outstanding, so it
    // can be unregistered from stopFreshFixRequest() / the fragment's onDestroyView.
    private LocationListener freshFixListener;

    public WeatherLocationProvider(Context context) {
        this.context = context.getApplicationContext();
        this.store = new WeatherLocationStore(this.context);
    }

    public List<WeatherLocation> list(User user) {
        double[] cityCoords = null;
        if (user != null) {
            cityCoords = cityRepository().coordinatesOf(user.getRegion(), user.getCity());
        }
        return WeatherLocations.build(
                new FieldRepository(context).all(), user, cityCoords, lastKnownFix(),
                store.customs());
    }

    public WeatherLocation selected(User user) {
        return selectedFrom(list(user));
    }

    /** The selection out of an already-built list, so a caller that just built one need not build it again. */
    public WeatherLocation selectedFrom(List<WeatherLocation> all) {
        return WeatherLocations.resolveSelected(all, store.selectedId());
    }

    private CityRepository cityRepository() {
        if (cityRepository == null) {
            cityRepository = new CityRepository(context);
        }
        return cityRepository;
    }

    public void select(String id) {
        store.select(id);
    }

    /**
     * The last position Android already knows, which is instant. We never block
     * the weather screen waiting for a fresh fix — a farmer opening the forecast
     * wants a number now, and a stale-by-minutes position picks the same
     * forecast grid cell as a fresh one.
     *
     * <p>Compares every enabled provider's fix by age and keeps the newest, then
     * discards it if it is still older than {@link #MAX_FIX_AGE_MS} — a leftover
     * passive/network fix from hours or days ago must not masquerade as "current".
     */
    private double[] lastKnownFix() {
        Location fix = newestLastKnownLocation();
        if (fix == null || System.currentTimeMillis() - fix.getTime() > MAX_FIX_AGE_MS) {
            return null;
        }
        return new double[]{fix.getLatitude(), fix.getLongitude()};
    }

    private Location newestLastKnownLocation() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        LocationManager manager = locationManager();
        if (manager == null) {
            return null;
        }
        Location newest = null;
        try {
            for (String provider : manager.getProviders(true)) {
                Location fix = manager.getLastKnownLocation(provider);
                if (fix != null && (newest == null || fix.getTime() > newest.getTime())) {
                    newest = fix;
                }
            }
        } catch (SecurityException e) {
            Log.i(TAG, "Location permission withdrawn while reading the last fix");
        }
        return newest;
    }

    private LocationManager locationManager() {
        return (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    /**
     * Asks for a single fresh fix in the background, so "My current location" can
     * become available (or more accurate) while the Weather screen is open.
     *
     * <p>Never requests the runtime permission and never shows any dialog — it
     * only proceeds when the permission is already granted. {@code onUpdated} runs
     * on the main thread once a fix arrives; the caller (WeatherFragment) is
     * expected to rebuild its dropdown and reload if GPS is the current selection.
     *
     * <p>The caller MUST call {@link #stopFreshFixRequest()} (e.g. from
     * {@code onDestroyView}) so the listener cannot fire into a dead view or leak
     * the fragment.
     */
    public void requestFreshFix(Runnable onUpdated) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationManager manager = locationManager();
        if (manager == null) {
            return;
        }
        stopFreshFixRequest();
        freshFixListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                stopFreshFixRequest();
                onUpdated.run();
            }

            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(String provider) {
            }

            @Override
            public void onProviderDisabled(String provider) {
            }
        };
        try {
            // requestSingleUpdate exists since API 24 (this app's minSdk) but is
            // deprecated on newer platforms; it remains the simplest one-shot API
            // that still works across the whole supported range.
            manager.requestSingleUpdate(
                    new android.location.Criteria(), freshFixListener, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.i(TAG, "Location permission withdrawn before a fresh fix could be requested");
            freshFixListener = null;
        }
    }

    /** Cancels any outstanding {@link #requestFreshFix} listener. Safe to call repeatedly. */
    public void stopFreshFixRequest() {
        if (freshFixListener == null) {
            return;
        }
        LocationManager manager = locationManager();
        if (manager != null) {
            try {
                manager.removeUpdates(freshFixListener);
            } catch (SecurityException e) {
                Log.i(TAG, "Location permission withdrawn while cancelling the fresh-fix request");
            }
        }
        freshFixListener = null;
    }
}
