package com.mvx.agriculture.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.mvx.agriculture.CityRepository;
import com.mvx.agriculture.R;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

/**
 * Pick where you farm on a satellite map, starting at the current GPS fix.
 *
 * The chosen point is snapped to the nearest district the app holds coordinates
 * for, so weather and mandi lookups keep working afterwards.
 */
public class LocationPickerDialog {

    public interface OnPicked {
        /** @param region and city are a district the app knows; lat/lon is the raw pick */
        void onPicked(String region, String city, double lat, double lon);
    }

    private static final OnlineTileSourceBase SATELLITE = new OnlineTileSourceBase(
            "EsriWorldImagery", 0, 19, 256, "",
            new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"}) {
        @Override
        public String getTileURLString(long mapTileIndex) {
            return getBaseUrl()
                    + MapTileIndex.getZoom(mapTileIndex) + "/"
                    + MapTileIndex.getY(mapTileIndex) + "/"
                    + MapTileIndex.getX(mapTileIndex);
        }
    };

    private final Activity activity;
    private final OnPicked callback;

    public LocationPickerDialog(Activity activity, OnPicked callback) {
        this.activity = activity;
        this.callback = callback;
    }

    public void show() {
        Configuration.getInstance().load(activity,
                PreferenceManager.getDefaultSharedPreferences(activity));
        Configuration.getInstance().setUserAgentValue("AgriCareAi/1.0 (Android farm mapping)");

        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_location_picker, null);
        MapView map = view.findViewById(R.id.pickerMap);
        TextView label = view.findViewById(R.id.pickerLabel);

        map.setTileSource(SATELLITE);
        map.setMultiTouchControls(true);
        map.getController().setZoom(15.0);

        CityRepository cities = new CityRepository(activity);

        // Start where the farmer is standing when we can; otherwise somewhere sane.
        Location fix = lastKnownLocation();
        GeoPoint start = fix != null
                ? new GeoPoint(fix.getLatitude(), fix.getLongitude())
                : new GeoPoint(20.5937, 78.9629);
        map.getController().setCenter(start);
        updateLabel(label, cities, start);

        map.addMapListener(new org.osmdroid.events.MapListener() {
            @Override
            public boolean onScroll(org.osmdroid.events.ScrollEvent event) {
                updateLabel(label, cities, (GeoPoint) map.getMapCenter());
                return false;
            }

            @Override
            public boolean onZoom(org.osmdroid.events.ZoomEvent event) {
                return false;
            }
        });

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.profile_pick_on_map)
                .setView(view)
                .setNegativeButton(R.string.action_cancel, null)
                .create();

        ((MaterialButton) view.findViewById(R.id.pickerConfirm)).setOnClickListener(v -> {
            GeoPoint centre = (GeoPoint) map.getMapCenter();
            String[] nearest = cities.nearestTo(centre.getLatitude(), centre.getLongitude());
            if (nearest != null && nearest.length == 2) {
                callback.onPicked(nearest[0], nearest[1],
                        centre.getLatitude(), centre.getLongitude());
            }
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> map.onDetach());
        dialog.show();
        map.onResume();
    }

    private void updateLabel(TextView label, CityRepository cities, GeoPoint point) {
        String[] nearest = cities.nearestTo(point.getLatitude(), point.getLongitude());
        label.setText(nearest == null
                ? ""
                : activity.getString(R.string.profile_location_set,
                        nearest[1] + ", " + nearest[0]));
    }

    @SuppressLint("MissingPermission")
    private Location lastKnownLocation() {
        boolean granted = ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(activity,
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!granted) {
            return null;
        }
        LocationManager manager =
                (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return null;
        }
        for (String provider : new String[]{
                LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            try {
                Location location = manager.getLastKnownLocation(provider);
                if (location != null) {
                    return location;
                }
            } catch (SecurityException | IllegalArgumentException e) {
                // provider unavailable; try the next
            }
        }
        return null;
    }
}
