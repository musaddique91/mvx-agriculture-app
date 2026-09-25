package com.mvx.agriculture.ui;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.mvx.agriculture.CityRepository;
import com.mvx.agriculture.MainShellActivity;
import com.mvx.agriculture.R;
import com.mvx.agriculture.User;
import com.mvx.agriculture.auth.PasswordHasher;
import com.mvx.agriculture.auth.SessionManager;
import com.mvx.agriculture.database.DatabaseHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.Calendar;
import java.util.List;

/**
 * Profile editing, split into the two things that actually differ.
 *
 * The username is the account's identity and stays fixed. Everything else —
 * where they farm, their date of birth — is editable in place, and the password
 * is changed separately because it needs the current one to authorise.
 */
public class ProfileFragment extends Fragment implements DatePickerDialog.OnDateSetListener {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private DatabaseHelper db;
    private SessionManager session;
    private CityRepository cityRepository;
    private User user;

    private MaterialAutoCompleteTextView regionInput, cityInput;
    private TextInputEditText birthdateInput, currentPassword, newPassword, confirmPassword;
    private TextInputLayout cityLayout, currentPasswordLayout, newPasswordLayout, confirmPasswordLayout;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        db = new DatabaseHelper(requireContext());
        session = new SessionManager(requireContext());
        cityRepository = new CityRepository(requireContext());

        MainShellActivity shell = (MainShellActivity) requireActivity();
        user = shell.currentUser();
        if (user == null) {
            return;
        }

        ((TextView) view.findViewById(R.id.profileUsername)).setText(user.getEmail());

        regionInput = view.findViewById(R.id.regionInput);
        cityInput = view.findViewById(R.id.cityInput);
        cityLayout = view.findViewById(R.id.cityLayout);
        birthdateInput = view.findViewById(R.id.birthdateInput);
        currentPassword = view.findViewById(R.id.currentPassword);
        newPassword = view.findViewById(R.id.newPassword);
        confirmPassword = view.findViewById(R.id.confirmPassword);
        currentPasswordLayout = view.findViewById(R.id.currentPasswordLayout);
        newPasswordLayout = view.findViewById(R.id.newPasswordLayout);
        confirmPasswordLayout = view.findViewById(R.id.confirmPasswordLayout);

        regionInput.setShowSoftInputOnFocus(false);
        cityInput.setShowSoftInputOnFocus(false);
        setUpRegionAndCity();

        birthdateInput.setText(user.getBirth());
        birthdateInput.setOnClickListener(v -> showDatePicker());
        ((TextInputLayout) view.findViewById(R.id.birthdateLayout))
                .setEndIconOnClickListener(v -> showDatePicker());

        ((MaterialButton) view.findViewById(R.id.pickOnMapButton)).setOnClickListener(v ->
                new LocationPickerDialog(requireActivity(), (region, city, lat, lon) -> {
                    regionInput.setText(region, false);
                    applyRegion(region);
                    cityInput.setText(city, false);
                    cityLayout.setError(null);
                }).show());

        ((MaterialButton) view.findViewById(R.id.saveDetailsButton))
                .setOnClickListener(v -> saveDetails());
        ((MaterialButton) view.findViewById(R.id.changePasswordButton))
                .setOnClickListener(v -> changePassword());
    }

    private void setUpRegionAndCity() {
        List<String> regions = cityRepository.getRegions();
        regionInput.setSimpleItems(regions.toArray(new String[0]));
        regionInput.setOnItemClickListener((parent, view, position, id) -> {
            String region = parent.getItemAtPosition(position).toString();
            applyRegion(region);
            cityInput.setText("", false);      // the old city belongs to the old region
            cityLayout.setError(null);
        });

        String region = user.getRegion();
        if (TextUtils.isEmpty(region) && !regions.isEmpty()) {
            region = regions.get(0);
        }
        if (!TextUtils.isEmpty(region)) {
            regionInput.setText(region, false);
            applyRegion(region);
        }
        if (!TextUtils.isEmpty(user.getCity())) {
            cityInput.setText(user.getCity(), false);
        }
    }

    private void applyRegion(String region) {
        cityInput.setSimpleItems(cityRepository.getCities(region).toArray(new String[0]));
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(requireContext(), this,
                calendar.get(Calendar.YEAR) - 25,
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    @Override
    public void onDateSet(android.widget.DatePicker view, int year, int month, int dayOfMonth) {
        birthdateInput.setText(dayOfMonth + "/" + (month + 1) + "/" + year);
    }

    private void saveDetails() {
        String region = text(regionInput);
        String city = text(cityInput);
        String birth = birthdateInput.getText() == null
                ? "" : birthdateInput.getText().toString().trim();

        cityLayout.setError(null);
        if (TextUtils.isEmpty(city)) {
            cityLayout.setError(getString(R.string.error_required));
            return;
        }

        db.updateContacts(new User(user.getId(), user.getEmail(), user.getPwd(),
                city, birth, region));
        // The token carries city and region, so re-issue it rather than leave it stale.
        session.logIn(user.getEmail(), city, region);
        user = db.findUsers(user.getEmail());

        Toast.makeText(requireContext(), R.string.profile_details_saved, Toast.LENGTH_SHORT).show();
        requireActivity().recreate();      // drawer header and Home read these
    }

    private void changePassword() {
        String current = text(currentPassword);
        String fresh = text(newPassword);
        String confirm = text(confirmPassword);

        currentPasswordLayout.setError(null);
        newPasswordLayout.setError(null);
        confirmPasswordLayout.setError(null);

        if (!PasswordHasher.verify(current, user.getPwd())) {
            currentPasswordLayout.setError(getString(R.string.profile_password_wrong));
            return;
        }
        if (fresh.length() < MIN_PASSWORD_LENGTH) {
            newPasswordLayout.setError(getString(R.string.error_password_short));
            return;
        }
        if (!fresh.equals(confirm)) {
            confirmPasswordLayout.setError(getString(R.string.edit_mismatch));
            return;
        }

        db.updatePassword(user.getId(), PasswordHasher.hash(fresh));
        user = db.findUsers(user.getEmail());

        currentPassword.setText("");
        newPassword.setText("");
        confirmPassword.setText("");
        Toast.makeText(requireContext(), R.string.profile_password_changed, Toast.LENGTH_LONG).show();
    }

    private static String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    private static String text(MaterialAutoCompleteTextView field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}
