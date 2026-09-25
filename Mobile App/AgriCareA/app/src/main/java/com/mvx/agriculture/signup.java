package com.mvx.agriculture;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.DatePicker;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.mvx.agriculture.theme.AppTheme;
import com.mvx.agriculture.theme.SoftTheme;

import com.mvx.agriculture.auth.PasswordHasher;
import com.mvx.agriculture.auth.SessionManager;
import com.mvx.agriculture.database.DatabaseHelper;
import com.mvx.agriculture.ui.LocationPickerDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Calendar;
import java.util.List;

public class signup extends AppCompatActivity implements DatePickerDialog.OnDateSetListener, com.mvx.agriculture.voice.VoiceInput.Host {

    private com.mvx.agriculture.voice.VoiceInput voiceInput;

    /** The picker opens either way; a fix just means it starts on the farmer. */
    private final ActivityResultLauncher<String[]> locationPermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> openLocationPicker());

    private static final int MIN_PASSWORD_LENGTH = 6;

    private DatabaseHelper database;
    private SessionManager session;
    private CityRepository cityRepository;

    private TextInputLayout usernameLayout, passwordLayout, regionLayout, cityLayout, birthdateLayout;
    private TextInputEditText username, password, birthdate;
    private MaterialAutoCompleteTextView regionInput, cityInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(AppTheme.current(this).styleRes);
        super.onCreate(savedInstanceState);
        LocaleManager.applyFont(this);
        setContentView(R.layout.activity_signup);
        SoftTheme.applyIfActive(findViewById(android.R.id.content));
        InsetsSupport.applyTo(this);
        voiceInput = new com.mvx.agriculture.voice.VoiceInput(this);
        voiceInput.attach(findViewById(android.R.id.content));

        database = new DatabaseHelper(this);
        session = new SessionManager(this);
        cityRepository = new CityRepository(this);

        usernameLayout = findViewById(R.id.usernameLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        regionLayout = findViewById(R.id.regionLayout);
        cityLayout = findViewById(R.id.cityLayout);
        birthdateLayout = findViewById(R.id.birthdateLayout);

        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        birthdate = findViewById(R.id.tvBirthdate);
        regionInput = findViewById(R.id.regionInput);
        cityInput = findViewById(R.id.cityInput);

        // inputType="none" alone does not stop every IME from popping up.
        regionInput.setShowSoftInputOnFocus(false);
        cityInput.setShowSoftInputOnFocus(false);

        setUpRegionAndCity();

        birthdate.setOnClickListener(v -> showDatePicker());
        birthdateLayout.setEndIconOnClickListener(v -> showDatePicker());

        findViewById(R.id.pickOnMapButton).setOnClickListener(v -> {
            if (hasLocationPermission()) {
                openLocationPicker();
            } else {
                locationPermission.launch(new String[]{
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION});
            }
        });

        MaterialButton signUp = findViewById(R.id.btnSignUp);
        MaterialButton toLogin = findViewById(R.id.linkLogin);

        signUp.setOnClickListener(v -> attemptSignUp());
        toLogin.setOnClickListener(v -> {
            startActivity(new Intent(this, login.class));
            finish();
        });
    }

    /** Region drives the city list; both come from assets/cities.csv. */
    private void setUpRegionAndCity() {
        List<String> regions = cityRepository.getRegions();
        regionInput.setSimpleItems(regions.toArray(new String[0]));

        regionInput.setOnItemClickListener((parent, view, position, id) -> {
            String region = parent.getItemAtPosition(position).toString();
            applyRegion(region);
            cityInput.setText("", false);      // the old city belongs to the old region
            cityLayout.setError(null);
        });

        if (!regions.isEmpty()) {
            String first = regions.get(0);
            regionInput.setText(first, false);
            applyRegion(first);
        }
    }

    private boolean hasLocationPermission() {
        return androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED
                || androidx.core.content.ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION)
                == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    private void openLocationPicker() {
        new LocationPickerDialog(this, (region, city, lat, lon) -> {
            regionInput.setText(region, false);
            applyRegion(region);
            cityInput.setText(city, false);
            regionLayout.setError(null);
            cityLayout.setError(null);
            Toast.makeText(this,
                    getString(R.string.profile_location_set, city + ", " + region),
                    Toast.LENGTH_SHORT).show();
        }).show();
    }

    private void applyRegion(String region) {
        cityInput.setSimpleItems(cityRepository.getCities(region).toArray(new String[0]));
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, this,
                calendar.get(Calendar.YEAR) - 25,   // a plausible adult default
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    private void attemptSignUp() {
        String user = text(username);
        String pass = text(password);
        String region = regionInput.getText() == null ? "" : regionInput.getText().toString().trim();
        String city = cityInput.getText() == null ? "" : cityInput.getText().toString().trim();
        String birth = text(birthdate);

        clearErrors();

        if (TextUtils.isEmpty(user)) {
            usernameLayout.setError(getString(R.string.error_required));
            return;
        }
        if (pass.length() < MIN_PASSWORD_LENGTH) {
            passwordLayout.setError(getString(R.string.error_password_short));
            return;
        }
        if (TextUtils.isEmpty(region)) {
            regionLayout.setError(getString(R.string.error_required));
            return;
        }
        if (TextUtils.isEmpty(city)) {
            cityLayout.setError(getString(R.string.error_required));
            return;
        }
        if (TextUtils.isEmpty(birth)) {
            birthdateLayout.setError(getString(R.string.error_pick_birthdate));
            return;
        }
        if (database.checkEmail(user)) {
            usernameLayout.setError(getString(R.string.error_username_taken));
            return;
        }

        database.addUsers(new User(user, PasswordHasher.hash(pass), city, birth, region));
        session.logIn(user, city, region);

        Intent intent = new Intent(this, MainShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void clearErrors() {
        usernameLayout.setError(null);
        passwordLayout.setError(null);
        regionLayout.setError(null);
        cityLayout.setError(null);
        birthdateLayout.setError(null);
    }

    @Override
    public void onDateSet(DatePicker view, int year, int month, int dayOfMonth) {
        birthdate.setText(dayOfMonth + "/" + (month + 1) + "/" + year);
        birthdateLayout.setError(null);
    }

    private static String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    @Override
    public com.mvx.agriculture.voice.VoiceInput voiceInput() {
        return voiceInput;
    }
}
