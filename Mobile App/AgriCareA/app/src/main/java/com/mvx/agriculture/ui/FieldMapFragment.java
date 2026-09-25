package com.mvx.agriculture.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Bundle;
import android.util.Log;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mvx.agriculture.BuildConfig;
import com.mvx.agriculture.CityRepository;
import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.R;
import com.mvx.agriculture.User;
import com.mvx.agriculture.data.CropData;
import com.mvx.agriculture.data.Field;
import com.mvx.agriculture.data.FieldRepository;
import com.mvx.agriculture.data.FieldStore;
import com.mvx.agriculture.data.GeocodeClient;
import com.mvx.agriculture.data.MapPositionStore;
import com.mvx.agriculture.data.FieldBoundaryDetector;
import com.mvx.agriculture.data.SamFieldSegmenter;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import org.osmdroid.config.Configuration;
import org.osmdroid.events.DelayedMapListener;
import org.osmdroid.events.MapAdapter;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.util.MapTileIndex;
import org.osmdroid.views.MapView;
import org.osmdroid.views.Projection;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.Polygon;
import org.osmdroid.views.overlay.TilesOverlay;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Mark a plot on satellite imagery: tap inside the field and let the app find its
 * border, then drag the corners to fit, or draw the corners by hand.
 *
 * OpenStreetMap tiles need no key or billing account, and an optional NASA
 * vegetation-index layer shows how the surrounding area is greening up.
 */
public class FieldMapFragment extends Fragment {

    /** When set, the map opens on this saved field instead of a blank map for a new one. */
    public static final String ARG_FIELD_ID = "field_id";

    private static final double EARTH_RADIUS_M = 6_378_137.0;
    private static final double SQM_PER_ACRE = 4046.8564224;
    private static final double SQM_PER_HECTARE = 10_000;

    /**
     * Esri World Imagery — global satellite basemap, no key and no billing account.
     * Attribution is required and shown under the map.
     */
    private static final OnlineTileSourceBase SATELLITE = new OnlineTileSourceBase(
            "EsriWorldImagery", 0, 18, 256, "",
            new String[]{"https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"}) {
        @Override
        public String getTileURLString(long mapTileIndex) {
            return getBaseUrl()
                    + MapTileIndex.getZoom(mapTileIndex) + "/"
                    + MapTileIndex.getY(mapTileIndex) + "/"
                    + MapTileIndex.getX(mapTileIndex);
        }
    };

    private MapView map;
    private TextView stepTitle, stepBody;
    private com.google.android.material.button.MaterialButton saveButton, undoButton, clearButton;
    private Polygon polygon;
    private TilesOverlay ndviOverlay;
    private final List<GeoPoint> corners = new ArrayList<>();

    /** Below this zoom a small plot is too few pixels for its edges to be found. */
    private static final double MIN_DETECT_ZOOM = 16.5;
    private static final double DETECT_ZOOM = 17.5;
    private static final int MAX_HISTORY = 50;

    /**
     * SELECT: tap a field, then find its border. MANUAL: every tap or "Add corner
     * here" adds a corner. EDIT: a border exists and its corners are being adjusted.
     * VIEW: a saved field is shown, read-only, until the farmer chooses to edit it.
     */
    private enum Mode { SELECT, MANUAL, EDIT, VIEW }

    /** The saved field being shown (VIEW) or re-bordered (EDIT); null for a new field. */
    private Field savedField;
    private View editActions;

    private Mode mode = Mode.SELECT;
    private com.google.android.material.button.MaterialButton primaryButton, modeButton;
    /** The point the farmer tapped inside their field, before its border is found. */
    private GeoPoint pin;
    private Marker pinMarker;
    /** Corner and "+" handles, rebuilt from {@link #corners} after every change. */
    private final List<Marker> handles = new ArrayList<>();
    /** Snapshots of {@link #corners} before each edit, so Undo steps back through drags too. */
    private final ArrayDeque<List<GeoPoint>> history = new ArrayDeque<>();
    private ExecutorService detector;
    private boolean detecting;
    /**
     * The AI model for this view's detector thread, loaded there on first use and
     * only ever touched there. Slot 0 holds it; a failed load leaves it empty for good.
     */
    private SamFieldSegmenter[] samSlot;
    private boolean[] samFailed;

    private final GeocodeClient geocoder = new GeocodeClient();
    private final List<GeocodeClient.Place> places = new ArrayList<>();
    private final android.os.Handler searchDebounce = new android.os.Handler();
    private MaterialCardView searchResultsCard;
    private PlaceAdapter placeAdapter;
    private View optionsCard;

    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                if (Boolean.TRUE.equals(granted.get(Manifest.permission.ACCESS_FINE_LOCATION))
                        || Boolean.TRUE.equals(granted.get(Manifest.permission.ACCESS_COARSE_LOCATION))) {
                    centreOnMe();
                } else {
                    toast(R.string.location_permission_needed);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // osmdroid needs a user agent and a cache directory before any tile loads.
        Configuration.getInstance().load(requireContext(),
                PreferenceManager.getDefaultSharedPreferences(requireContext()));
        // OSM's volunteer tile servers reject default and com.example agents outright,
        // so identify the app properly.
        Configuration.getInstance().setUserAgentValue("AgriCareAi/1.0 (Android farm mapping)");
        return inflater.inflate(R.layout.fragment_field_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        map = view.findViewById(R.id.map);
        stepTitle = view.findViewById(R.id.stepTitle);
        stepBody = view.findViewById(R.id.stepBody);
        saveButton = view.findViewById(R.id.saveButton);

        // Satellite imagery, not a street map: you pick a field out by its crop and
        // its edges, which a road map simply does not show.
        map.setTileSource(SATELLITE);
        map.setMultiTouchControls(true);
        restorePosition();

        polygon = new Polygon(map);
        polygon.setFillColor(0x552E7D32);
        polygon.setStrokeColor(0xFF2E7D32);
        polygon.setStrokeWidth(4f);
        map.getOverlays().add(polygon);
        // A new field starts on a blank map; a saved one opens on its own outline.
        int fieldId = getArguments() == null ? 0 : getArguments().getInt(ARG_FIELD_ID);
        if (fieldId > 0) {
            savedField = new FieldRepository(requireContext()).byId(fieldId);
        }

        // What a tap means depends on the step: pick a field, or add a corner by hand.
        // While adjusting a border the corners are moved by their own handles.
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint point) {
                hideResults();
                if (mode == Mode.SELECT) {
                    placePin(point);
                } else if (mode == Mode.MANUAL) {
                    pushHistory();
                    corners.add(point);
                    renderBoundary();
                }
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint point) {
                return false;
            }
        }));

        // Which "+" handles fit depends on the zoom, so re-lay them out once zooming settles.
        map.addMapListener(new DelayedMapListener(new MapAdapter() {
            @Override
            public boolean onZoom(ZoomEvent event) {
                if (mode == Mode.EDIT || mode == Mode.MANUAL) {
                    renderBoundary();
                }
                return false;
            }
        }, 200));

        detector = Executors.newSingleThreadExecutor();
        detecting = false;
        samSlot = new SamFieldSegmenter[1];
        samFailed = new boolean[1];
        undoButton = view.findViewById(R.id.undoButton);
        clearButton = view.findViewById(R.id.clearButton);
        primaryButton = view.findViewById(R.id.addCornerButton);
        modeButton = view.findViewById(R.id.detectButton);
        primaryButton.setOnClickListener(v -> {
            if (mode == Mode.VIEW) {
                ((MainShellActivity) requireActivity()).openFieldDetail(savedField.id);
            } else if (mode == Mode.SELECT) {
                markField();
            } else if (mode == Mode.MANUAL) {
                // The crosshair is the contract: a corner lands exactly where it sits.
                pushHistory();
                corners.add((GeoPoint) map.getMapCenter());
                renderBoundary();
            }
        });
        modeButton.setOnClickListener(v -> switchDrawingMode());
        view.findViewById(R.id.locateButton).setOnClickListener(v -> requestLocation());
        undoButton.setOnClickListener(v -> undo());
        clearButton.setOnClickListener(v -> startOver());
        saveButton.setOnClickListener(v -> save());

        MaterialSwitch satelliteSwitch = view.findViewById(R.id.satelliteSwitch);
        satelliteSwitch.setChecked(true);
        satelliteSwitch.setOnCheckedChangeListener((button, checked) -> {
            map.setTileSource(checked ? SATELLITE : TileSourceFactory.MAPNIK);
            map.invalidate();
        });

        setUpSearch(view);

        optionsCard = view.findViewById(R.id.optionsCard);
        view.findViewById(R.id.layersButton).setOnClickListener(v -> {
            boolean showing = optionsCard.getVisibility() == View.VISIBLE;
            optionsCard.setVisibility(showing ? View.GONE : View.VISIBLE);
            hideResults();
        });

        TextView ndviNote = view.findViewById(R.id.ndviNote);
        MaterialSwitch ndviSwitch = view.findViewById(R.id.ndviSwitch);
        ndviSwitch.setOnCheckedChangeListener((button, checked) -> {
            ndviNote.setVisibility(checked ? View.VISIBLE : View.GONE);
            toggleNdvi(checked);
        });

        editActions = view.findViewById(R.id.editActions);
        if (savedField != null && savedField.boundary.size() >= 3) {
            mode = Mode.VIEW;
            for (Field.Point point : savedField.boundary) {
                corners.add(new GeoPoint(point.lat, point.lon));
            }
            renderBoundary();
            // Bounds are only known once the map has been laid out.
            map.addOnFirstLayoutListener((v, left, top, right, bottom) ->
                    map.zoomToBoundingBox(polygon.getBounds(), false, 120));
        }
        updateArea();
    }

    /** Type a village or town and jump the map there. */
    private void setUpSearch(View view) {
        EditText searchInput = view.findViewById(R.id.searchInput);
        searchResultsCard = view.findViewById(R.id.searchResultsCard);
        RecyclerView results = view.findViewById(R.id.searchResults);

        placeAdapter = new PlaceAdapter();
        results.setLayoutManager(new LinearLayoutManager(requireContext()));
        results.setAdapter(placeAdapter);

        searchInput.addTextChangedListener(new SimpleTextWatcher(text -> {
            searchDebounce.removeCallbacksAndMessages(null);
            if (text.trim().length() < 3) {
                hideResults();
                return;
            }
            // Nominatim asks callers not to fire on every keystroke.
            searchDebounce.postDelayed(() -> runSearch(text.trim()), 450);
        }));

        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchDebounce.removeCallbacksAndMessages(null);
                runSearch(v.getText().toString().trim());
                hideKeyboard(v);
                return true;
            }
            return false;
        });
    }

    private void runSearch(String query) {
        if (query.isEmpty()) {
            return;
        }
        geocoder.search(query, new GeocodeClient.Listener() {
            @Override
            public void onPlaces(List<GeocodeClient.Place> found) {
                if (!isAdded()) {
                    return;
                }
                if (found.isEmpty()) {
                    hideResults();
                    toast(R.string.field_search_none);
                    return;
                }
                places.clear();
                places.addAll(found);
                placeAdapter.notifyDataSetChanged();
                searchResultsCard.setVisibility(View.VISIBLE);
                optionsCard.setVisibility(View.GONE);
            }

            @Override
            public void onError() {
                if (isAdded()) {
                    hideResults();
                    toast(R.string.field_search_none);
                }
            }
        });
    }

    private void goTo(GeocodeClient.Place place) {
        hideResults();
        View focused = requireActivity().getCurrentFocus();
        if (focused != null) {
            hideKeyboard(focused);
        }
        map.getController().setZoom(17.0);
        map.getController().animateTo(new GeoPoint(place.lat, place.lon));
    }

    private void hideResults() {
        if (searchResultsCard != null) {
            searchResultsCard.setVisibility(View.GONE);
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager manager = (InputMethodManager)
                requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.Holder> {

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_place, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            GeocodeClient.Place place = places.get(position);
            holder.name.setText(place.name);
            holder.itemView.setOnClickListener(v -> goTo(place));
        }

        @Override
        public int getItemCount() {
            return places.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView name;

            Holder(@NonNull View itemView) {
                super(itemView);
                name = itemView.findViewById(R.id.placeName);
            }
        }
    }

    /**
     * Opens where the work is, not where the account was registered.
     *
     * Last position first, then the most recently mapped field, then the home
     * town as a last resort.
     */
    private void restorePosition() {
        double[] last = MapPositionStore.load(requireContext());
        if (last != null) {
            map.getController().setZoom(last[2]);
            map.getController().setCenter(new GeoPoint(last[0], last[1]));
            return;
        }

        List<Field> saved = new FieldRepository(requireContext()).all();
        for (Field field : saved) {
            Field.Point centre = field.centroid();
            if (centre != null) {
                map.getController().setZoom(17.0);
                map.getController().setCenter(new GeoPoint(centre.lat, centre.lon));
                return;
            }
        }

        map.getController().setZoom(17.0);
        User user = ((MainShellActivity) requireActivity()).currentUser();
        if (user == null) {
            return;
        }
        double[] coordinates = new CityRepository(requireContext())
                .coordinatesOf(user.getRegion(), user.getCity());
        if (coordinates != null) {
            map.getController().setCenter(new GeoPoint(coordinates[0], coordinates[1]));
        }
    }

    /**
     * Draws the plots already mapped, so a new boundary can be placed next to them
     * rather than accidentally on top of one.
     */
    private void placePin(GeoPoint point) {
        pin = point;
        if (pinMarker == null) {
            pinMarker = new Marker(map);
            pinMarker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_field_pin));
            pinMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            pinMarker.setInfoWindow(null);
            map.getOverlays().add(pinMarker);
        }
        pinMarker.setPosition(point);
        map.invalidate();
        updateArea();
    }

    private void removePin() {
        pin = null;
        if (pinMarker != null) {
            map.getOverlays().remove(pinMarker);
            pinMarker = null;
        }
    }

    /**
     * Finds the border of the field under the pin from the imagery on screen.
     *
     * The map is drawn into a bitmap with every overlay hidden, so the detector
     * sees exactly the satellite picture the farmer sees, already downloaded, and
     * the outline maps straight back through the same projection.
     */
    private void markField() {
        if (detecting) {
            return;
        }
        GeoPoint target = pin != null ? pin : (GeoPoint) map.getMapCenter();
        if (map.getZoomLevelDouble() < MIN_DETECT_ZOOM) {
            placePin(target);
            map.getController().animateTo(target, DETECT_ZOOM, 700L);
            toast(R.string.field_zoom_in);
            return;
        }
        Bitmap image = captureImagery();
        if (image == null) {
            toast(R.string.field_mark_none);
            return;
        }
        final Projection projection = map.getProjection();
        final Point seed = projection.toPixels(target, null);
        final int width = image.getWidth();
        final int height = image.getHeight();
        final Context appContext = requireContext().getApplicationContext();
        final SamFieldSegmenter[] slot = samSlot;
        final boolean[] failed = samFailed;

        detecting = true;
        updateArea();
        detector.execute(() -> {
            int[][] outline = null;
            // The AI model tells apart neighbouring plots of the same crop, which colour
            // and edges alone cannot; the classical detector stays as the fallback.
            if (seed.x >= 0 && seed.y >= 0 && seed.x < width && seed.y < height) {
                try {
                    if (slot[0] == null && !failed[0]) {
                        slot[0] = SamFieldSegmenter.load(appContext);
                    }
                    if (slot[0] != null) {
                        outline = slot[0].segment(image, seed.x, seed.y);
                    }
                } catch (Exception | OutOfMemoryError e) {
                    Log.w("FieldMap", "On-device AI field detection failed; using edge detection", e);
                    if (slot[0] == null) {
                        failed[0] = true;
                    }
                }
            }
            if (outline == null) {
                int[] pixels = new int[width * height];
                image.getPixels(pixels, 0, width, 0, 0, width, height);
                outline = FieldBoundaryDetector.detect(pixels, width, height, seed.x, seed.y);
            }
            image.recycle();
            final int[][] found = outline;
            map.post(() -> onOutline(found, projection));
        });
    }

    private void onOutline(int[][] outline, Projection projection) {
        if (!isAdded() || getView() == null) {
            return;
        }
        detecting = false;
        if (outline == null) {
            toast(R.string.field_mark_none);
            updateArea();
            return;
        }
        pushHistory();
        corners.clear();
        for (int[] point : outline) {
            corners.add((GeoPoint) projection.fromPixels(point[0], point[1]));
        }
        removePin();
        mode = Mode.EDIT;
        renderBoundary();
    }

    /** The map as the farmer sees it, minus pins, outlines and the vegetation layer. */
    private Bitmap captureImagery() {
        if (map.getWidth() == 0 || map.getHeight() == 0) {
            return null;
        }
        List<Overlay> hidden = new ArrayList<>();
        for (Overlay overlay : map.getOverlays()) {
            if (overlay.isEnabled()) {
                overlay.setEnabled(false);
                hidden.add(overlay);
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(map.getWidth(), map.getHeight(), Bitmap.Config.ARGB_8888);
        try {
            map.draw(new Canvas(bitmap));
        } finally {
            for (Overlay overlay : hidden) {
                overlay.setEnabled(true);
            }
            map.invalidate();
        }
        return bitmap;
    }

    /** Redraws the outline and its handles from {@link #corners}. */
    private void renderBoundary() {
        for (Marker handle : handles) {
            map.getOverlays().remove(handle);
        }
        handles.clear();
        polygon.setPoints(new ArrayList<>(corners));

        if (mode == Mode.VIEW) {
            map.invalidate();
            updateArea();
            return;
        }
        // "+" handles first, so corner handles sit on top and win a tap where they overlap.
        if (corners.size() >= 3) {
            for (int i = 0; i < corners.size(); i++) {
                GeoPoint a = corners.get(i);
                GeoPoint b = corners.get((i + 1) % corners.size());
                final int insertAt = i + 1;
                if (screenDistance(a, b) < minHandleGap()) {
                    continue;   // too short on screen: a "+" would sit on the corners; zoom in to get it
                }
                Marker add = handle(new GeoPoint((a.getLatitude() + b.getLatitude()) / 2,
                        (a.getLongitude() + b.getLongitude()) / 2), R.drawable.ic_corner_add);
                add.setOnMarkerClickListener((marker, mapView) -> {
                    pushHistory();
                    corners.add(insertAt, marker.getPosition());
                    renderBoundary();
                    return true;
                });
            }
        }
        for (int i = 0; i < corners.size(); i++) {
            final int index = i;
            Marker corner = handle(corners.get(i), R.drawable.ic_field_corner_marker);
            corner.setDraggable(true);
            corner.setOnMarkerDragListener(new Marker.OnMarkerDragListener() {
                @Override
                public void onMarkerDragStart(Marker marker) {
                    pushHistory();
                }

                @Override
                public void onMarkerDrag(Marker marker) {
                    corners.set(index, marker.getPosition());
                    polygon.setPoints(new ArrayList<>(corners));
                    map.invalidate();
                }

                @Override
                public void onMarkerDragEnd(Marker marker) {
                    corners.set(index, marker.getPosition());
                    renderBoundary();   // moves the "+" handles to the new midpoints
                }
            });
            corner.setOnMarkerClickListener((marker, mapView) -> {
                confirmRemoveCorner(index);
                return true;
            });
        }
        map.invalidate();
        updateArea();
    }

    private double screenDistance(GeoPoint a, GeoPoint b) {
        Point pa = map.getProjection().toPixels(a, null);
        Point pb = map.getProjection().toPixels(b, null);
        return Math.hypot(pa.x - pb.x, pa.y - pb.y);
    }

    /** Room for a 48 dp "+" between two 48 dp corner handles. */
    private double minHandleGap() {
        return 96 * getResources().getDisplayMetrics().density;
    }

    private Marker handle(GeoPoint position, int iconRes) {
        Marker marker = new Marker(map);
        marker.setPosition(position);
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
        marker.setIcon(fingerSized(iconRes));
        marker.setInfoWindow(null);
        map.getOverlays().add(marker);
        handles.add(marker);
        return marker;
    }

    /**
     * A marker's touch area is its drawable's size. The corner dot is drawn small so
     * it does not hide the field edge; transparent padding makes it a 48 dp target.
     */
    private Drawable fingerSized(int iconRes) {
        Drawable icon = ContextCompat.getDrawable(requireContext(), iconRes);
        int target = Math.round(48 * getResources().getDisplayMetrics().density);
        int inset = Math.max(0, (target - icon.getIntrinsicWidth()) / 2);
        return new InsetDrawable(icon, inset);
    }

    private void confirmRemoveCorner(int index) {
        if (mode != Mode.MANUAL && corners.size() <= 3) {
            toast(R.string.field_min_corners);
            return;
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.field_remove_corner_title)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.field_remove, (dialog, which) -> {
                    pushHistory();
                    corners.remove(index);
                    renderBoundary();
                })
                .show();
    }

    private void pushHistory() {
        history.push(new ArrayList<>(corners));
        while (history.size() > MAX_HISTORY) {
            history.removeLast();
        }
    }

    private void undo() {
        if (history.isEmpty()) {
            return;
        }
        corners.clear();
        corners.addAll(history.pop());
        if (corners.isEmpty() && mode == Mode.EDIT) {
            mode = Mode.SELECT;
        }
        renderBoundary();
    }

    /** Clears the border (undoable) and goes back to picking a field, or to an empty hand drawing. */
    private void startOver() {
        if (!corners.isEmpty()) {
            pushHistory();
        }
        corners.clear();
        removePin();
        if (mode == Mode.EDIT) {
            mode = Mode.SELECT;
        }
        renderBoundary();
    }

    private void switchDrawingMode() {
        if (mode == Mode.VIEW) {
            // Edit border: the saved corners become draggable; Done updates this field.
            history.clear();
            mode = Mode.EDIT;
            renderBoundary();
            return;
        }
        if (!corners.isEmpty()) {
            pushHistory();
        }
        corners.clear();
        removePin();
        mode = mode == Mode.MANUAL ? Mode.SELECT : Mode.MANUAL;
        renderBoundary();
    }

    /** Names the plot and stores it, so it gains advice, notes and a history. */
    private void save() {
        double squareMetres = areaSquareMetres();
        if (squareMetres <= 0) {
            toast(R.string.field_need_points);
            return;
        }
        final double acres = squareMetres / SQM_PER_ACRE;

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_save_field, null);
        com.mvx.agriculture.voice.VoiceInput.attachIn(dialogView);
        TextInputEditText nameInput = dialogView.findViewById(R.id.fieldNameInput);
        MaterialAutoCompleteTextView cropInput = dialogView.findViewById(R.id.fieldCropInput);
        cropInput.setShowSoftInputOnFocus(false);
        cropInput.setSimpleItems(new CropData(requireContext()).names().toArray(new String[0]));
        if (savedField != null) {
            nameInput.setText(savedField.name);
            if (savedField.crop != null) {
                cropInput.setText(savedField.crop, false);
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.field_save)
                .setView(dialogView)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save, (dialog, which) -> {
                    // Re-bordering keeps the field's id, sowing date and history.
                    Field field = savedField != null ? savedField : new Field();
                    String name = nameInput.getText() == null
                            ? "" : nameInput.getText().toString().trim();
                    field.name = name.isEmpty()
                            ? getString(R.string.field_new) : name;
                    field.crop = cropInput.getText() == null
                            ? null : cropInput.getText().toString().trim();
                    field.areaAcres = acres;
                    field.boundary.clear();
                    for (GeoPoint corner : corners) {
                        field.boundary.add(new Field.Point(
                                corner.getLatitude(), corner.getLongitude()));
                    }
                    long id = new FieldRepository(requireContext()).save(field);

                    // The calculators reuse the most recently measured area.
                    FieldStore.saveAcres(requireContext(), acres);
                    toast(R.string.field_saved_named);
                    // Stay on the map, showing the field just saved.
                    ((MainShellActivity) requireActivity()).openFieldOnMap((int) id);
                })
                .show();
    }

    /** Keeps the banner and the buttons honest about where the farmer is. */
    private void updateArea() {
        int count = corners.size();
        boolean complete = count >= 3;
        saveButton.setEnabled(complete && !detecting);
        undoButton.setEnabled(!history.isEmpty() && !detecting);
        clearButton.setEnabled((count > 0 || pin != null) && !detecting);

        if (mode == Mode.VIEW) {
            editActions.setVisibility(View.GONE);
            primaryButton.setVisibility(View.VISIBLE);
            primaryButton.setEnabled(true);
            primaryButton.setText(R.string.field_details);
            primaryButton.setIconResource(R.drawable.ic_info);
            modeButton.setText(R.string.field_edit_border);
            modeButton.setIconResource(R.drawable.ic_edit);
            double squareMetres = areaSquareMetres();
            String area = getString(R.string.field_step_ready_title,
                    squareMetres / SQM_PER_ACRE, squareMetres / SQM_PER_HECTARE);
            stepTitle.setText(savedField.name);
            stepBody.setText(savedField.crop == null || savedField.crop.isEmpty()
                    ? area : getString(R.string.field_view_body, savedField.crop, area));
            return;
        }
        editActions.setVisibility(View.VISIBLE);
        primaryButton.setVisibility(mode == Mode.EDIT ? View.GONE : View.VISIBLE);
        primaryButton.setEnabled(!detecting);
        boolean manual = mode == Mode.MANUAL;
        primaryButton.setText(manual ? R.string.field_add_corner : R.string.field_mark_this);
        primaryButton.setIconResource(manual ? R.drawable.ic_add_location : R.drawable.ic_fields);
        modeButton.setText(manual ? R.string.field_mark_auto : R.string.field_draw_by_hand);
        modeButton.setIconResource(manual ? R.drawable.ic_fields : R.drawable.ic_edit);

        if (detecting) {
            stepTitle.setText(R.string.field_marking);
            stepBody.setText(R.string.field_marking_body);
            return;
        }
        if (complete) {
            double squareMetres = areaSquareMetres();
            stepTitle.setText(getString(R.string.field_step_ready_title,
                    squareMetres / SQM_PER_ACRE, squareMetres / SQM_PER_HECTARE));
            stepBody.setText(manual
                    ? getString(R.string.field_step_ready_body, count)
                    : getString(R.string.field_step_edit_body));
            return;
        }
        if (mode == Mode.SELECT) {
            stepTitle.setText(pin == null ? R.string.field_step_select_title : R.string.field_step_selected_title);
            stepBody.setText(pin == null ? R.string.field_step_select_body : R.string.field_step_selected_body);
            return;
        }
        if (count == 0) {
            stepTitle.setText(R.string.field_step_start_title);
            stepBody.setText(R.string.field_step_start_body);
            return;
        }
        stepTitle.setText(getString(R.string.field_step_more_title, count));
        stepBody.setText(R.string.field_step_more_body);
    }

    /**
     * Shoelace area on an equirectangular projection about the plot's own latitude.
     * Over a few hundred metres that is accurate to well under a percent, and it
     * avoids pulling in a geodesy library for a field boundary.
     */
    private double areaSquareMetres() {
        if (corners.size() < 3) {
            return 0;
        }
        double latitudeSum = 0;
        for (GeoPoint point : corners) {
            latitudeSum += point.getLatitude();
        }
        double meanLatitudeRad = Math.toRadians(latitudeSum / corners.size());
        double metresPerDegreeLat = Math.PI * EARTH_RADIUS_M / 180.0;
        double metresPerDegreeLon = metresPerDegreeLat * Math.cos(meanLatitudeRad);

        double sum = 0;
        for (int i = 0; i < corners.size(); i++) {
            GeoPoint a = corners.get(i);
            GeoPoint b = corners.get((i + 1) % corners.size());
            double ax = a.getLongitude() * metresPerDegreeLon;
            double ay = a.getLatitude() * metresPerDegreeLat;
            double bx = b.getLongitude() * metresPerDegreeLon;
            double by = b.getLatitude() * metresPerDegreeLat;
            sum += ax * by - bx * ay;
        }
        return Math.abs(sum) / 2.0;
    }

    /** NASA GIBS vegetation index: free, keyless, 375 m per pixel, refreshed every 8 days. */
    private void toggleNdvi(boolean show) {
        if (!show) {
            if (ndviOverlay != null) {
                map.getOverlays().remove(ndviOverlay);
                ndviOverlay = null;
                map.invalidate();
            }
            return;
        }
        if (ndviOverlay != null) {
            return;
        }

        // The 8-day composite lags, so ask for a date that is certainly published.
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DAY_OF_YEAR, -10);
        final String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                .format(new Date(calendar.getTimeInMillis()));

        OnlineTileSourceBase source = new OnlineTileSourceBase("VIIRS_NDVI", 1, 8, 256, ".png",
                new String[]{"https://gibs.earthdata.nasa.gov/wmts/epsg3857/best/"}) {
            @Override
            public String getTileURLString(long mapTileIndex) {
                return getBaseUrl()
                        + "VIIRS_SNPP_NDVI_8Day/default/" + date
                        + "/GoogleMapsCompatible_Level8/"
                        + MapTileIndex.getZoom(mapTileIndex) + "/"
                        + MapTileIndex.getY(mapTileIndex) + "/"
                        + MapTileIndex.getX(mapTileIndex) + ".png";
            }
        };

        ndviOverlay = new TilesOverlay(
                new org.osmdroid.tileprovider.MapTileProviderBasic(requireContext(), source), requireContext());
        ndviOverlay.setLoadingBackgroundColor(android.graphics.Color.TRANSPARENT);
        ndviOverlay.setColorFilter(null);
        map.getOverlays().add(0, ndviOverlay);
        map.invalidate();
    }

    private void requestLocation() {
        if (hasLocationPermission()) {
            centreOnMe();
        } else {
            locationPermission.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void centreOnMe() {
        Location location = lastKnownLocation();
        if (location == null) {
            requestSingleFix();
            return;
        }
        map.getController().animateTo(new GeoPoint(location.getLatitude(), location.getLongitude()));
    }

    @SuppressLint("MissingPermission")
    private Location lastKnownLocation() {
        if (!hasLocationPermission()) {
            return null;
        }
        LocationManager manager =
                (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
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
                // provider missing on this device; try the next one
            }
        }
        return null;
    }

    /** No cached fix yet: ask for one update rather than leaving the button dead. */
    @SuppressLint("MissingPermission")
    private void requestSingleFix() {
        if (!hasLocationPermission()) {
            requestLocation();
            return;
        }
        LocationManager manager =
                (LocationManager) requireContext().getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            toast(R.string.location_unavailable);
            return;
        }
        try {
            manager.requestSingleUpdate(LocationManager.GPS_PROVIDER, new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    if (isAdded()) {
                        map.getController().animateTo(
                                new GeoPoint(location.getLatitude(), location.getLongitude()));
                    }
                }

                @Override
                public void onProviderDisabled(@NonNull String provider) {
                }

                @Override
                public void onProviderEnabled(@NonNull String provider) {
                }
            }, null);
        } catch (SecurityException | IllegalArgumentException e) {
            toast(R.string.location_unavailable);
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void toast(int messageRes) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();
        GeoPoint centre = (GeoPoint) map.getMapCenter();
        MapPositionStore.save(requireContext(),
                centre.getLatitude(), centre.getLongitude(), map.getZoomLevelDouble());
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        searchDebounce.removeCallbacksAndMessages(null);
        if (detector != null) {
            final SamFieldSegmenter[] slot = samSlot;
            // Closed on the detector thread, after any detection still running there.
            detector.execute(() -> {
                if (slot[0] != null) {
                    slot[0].close();
                    slot[0] = null;
                }
            });
            detector.shutdown();
        }
    }
}
