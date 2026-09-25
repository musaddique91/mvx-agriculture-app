package com.mvx.agriculture;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.mvx.agriculture.theme.AppTheme;
import com.mvx.agriculture.theme.SoftTheme;
import androidx.core.graphics.Insets;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.mvx.agriculture.auth.SessionManager;
import com.mvx.agriculture.database.DatabaseHelper;
import com.mvx.agriculture.ui.CalculatorFragment;
import com.mvx.agriculture.ui.ChatFragment;
import com.mvx.agriculture.ui.CropCalendarFragment;
import com.mvx.agriculture.ui.EncyclopediaFragment;
import com.mvx.agriculture.ui.FieldDetailFragment;
import com.mvx.agriculture.ui.FieldMapFragment;
import com.mvx.agriculture.ui.FieldsFragment;
import com.mvx.agriculture.ui.HomeFragment;
import com.mvx.agriculture.ui.MandiFragment;
import com.mvx.agriculture.ui.MoneyFragment;
import com.mvx.agriculture.ui.ProfileFragment;
import com.mvx.agriculture.ui.ScanFragment;
import com.mvx.agriculture.ui.SchemesFragment;
import com.mvx.agriculture.ui.WeatherFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;

/**
 * The single signed-in screen: a drawer for the full menu, bottom tabs for the five
 * things farmers open most, and one fragment container for everything else.
 */
public class MainShellActivity extends AppCompatActivity {

    /** Home asks the shell to switch tabs when a tile is tapped. */
    public interface Navigator {
        void openDestination(int menuId);
    }

    private static final String STATE_DESTINATION = "destination";

    private DrawerLayout drawer;
    private BottomNavigationView bottomNav;
    private MaterialToolbar toolbar;
    private SessionManager session;
    private DatabaseHelper db;
    private String username;
    private int destination = R.id.nav_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(AppTheme.current(this).styleRes);
        super.onCreate(savedInstanceState);
        LocaleManager.applyFont(this);
        setContentView(R.layout.activity_shell);
        SoftTheme.applyIfActive(findViewById(android.R.id.content));

        session = new SessionManager(this);
        db = new DatabaseHelper(this);
        username = session.username();
        if (username == null) {
            session.logOutTo(this, HomeActivity.class);
            finish();
            return;
        }

        drawer = findViewById(R.id.drawerLayout);
        bottomNav = findViewById(R.id.bottomNav);
        toolbar = findViewById(R.id.toolbar);
        NavigationView navigationView = findViewById(R.id.navigationView);

        applyInsets();

        toolbar.setNavigationOnClickListener(v -> drawer.openDrawer(GravityCompat.START));
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_language) {
                showLanguagePicker();
                return true;
            }
            if (id == R.id.action_theme) {
                showThemePicker();
                return true;
            }
            return false;
        });

        bottomNav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });

        navigationView.setNavigationItemSelectedListener(item -> {
            drawer.closeDrawer(GravityCompat.START);
            handleDrawerChoice(item.getItemId());
            return true;
        });

        fillDrawerHeader(navigationView);

        // Fragment views are created after the activity's, so catch each one.
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentViewCreated(
                            @NonNull androidx.fragment.app.FragmentManager fm,
                            @NonNull androidx.fragment.app.Fragment f,
                            @NonNull View v, android.os.Bundle state) {
                        SoftTheme.applyIfActive(v);
                    }
                }, true);

        destination = savedInstanceState == null
                ? R.id.nav_home
                : savedInstanceState.getInt(STATE_DESTINATION, R.id.nav_home);
        show(destination);
        syncBottomNav(destination);
        if (savedInstanceState == null) {
            openFromNotification(getIntent());
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_DESTINATION, destination);
    }

    @Override
    public void onBackPressed() {
        if (drawer != null && drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else if (destination != R.id.nav_home) {
            show(R.id.nav_home);           // every screen falls back to Home, not the login page
            syncBottomNav(R.id.nav_home);
        } else {
            super.onBackPressed();
        }
    }

    /** Pads the toolbar for the status bar and the bottom bar for the navigation bar. */
    private void applyInsets() {
        View content = findViewById(R.id.shellContent);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            v.setPadding(bars.left, bars.top, bars.right, 0);
            bottomNav.setPadding(0, 0, 0, bars.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(content);
    }

    private void fillDrawerHeader(NavigationView navigationView) {
        View header = navigationView.getHeaderView(0);
        User user = db.findUsers(username);
        ((TextView) header.findViewById(R.id.navName)).setText(username);
        TextView location = header.findViewById(R.id.navLocation);
        if (user != null) {
            location.setText(user.getLocation());
        }
    }

    private void handleDrawerChoice(int id) {
        if (id == R.id.drawer_logout) {
            confirmLogout();
        } else if (id == R.id.drawer_language) {
            showLanguagePicker();
        } else if (id == R.id.drawer_theme) {
            showThemePicker();
        } else if (id == R.id.drawer_about) {
            startActivity(new android.content.Intent(this, Infos.class));
        } else {
            show(drawerToDestination(id));
            syncBottomNav(drawerToDestination(id));
        }
    }

    private int drawerToDestination(int id) {
        if (id == R.id.drawer_scan) return R.id.nav_scan;
        if (id == R.id.drawer_market) return R.id.drawer_market;
        if (id == R.id.drawer_weather) return R.id.nav_weather;
        if (id == R.id.drawer_chat) return R.id.nav_chat;
        if (id == R.id.drawer_encyclopedia) return R.id.drawer_encyclopedia;
        if (id == R.id.drawer_calendar) return R.id.drawer_calendar;
        if (id == R.id.drawer_calculator) return R.id.drawer_calculator;
        if (id == R.id.drawer_field) return R.id.drawer_field;
        if (id == R.id.drawer_fields) return R.id.nav_fields;
        if (id == R.id.drawer_money) return R.id.drawer_money;
        if (id == R.id.drawer_schemes) return R.id.drawer_schemes;
        if (id == R.id.drawer_profile) return R.id.drawer_profile;
        return R.id.nav_home;
    }

    /** Keeps the bottom bar's highlight honest when a drawer item owns the screen. */
    private void syncBottomNav(int id) {
        bottomNav.setOnItemSelectedListener(null);
        if (id == R.id.nav_home || id == R.id.nav_scan || id == R.id.nav_fields
                || id == R.id.nav_weather || id == R.id.nav_chat) {
            bottomNav.setSelectedItemId(id);
        } else {
            // A tools screen: clear the highlight by checking nothing.
            bottomNav.getMenu().setGroupCheckable(0, true, false);
            for (int i = 0; i < bottomNav.getMenu().size(); i++) {
                bottomNav.getMenu().getItem(i).setChecked(false);
            }
            bottomNav.getMenu().setGroupCheckable(0, true, true);
        }
        bottomNav.setOnItemSelectedListener(item -> {
            show(item.getItemId());
            return true;
        });
    }

    public void show(int id) {
        destination = id;
        Fragment fragment;
        int title;

        if (id == R.id.nav_scan) {
            fragment = new ScanFragment();
            title = R.string.title_scan;
        } else if (id == R.id.nav_fields || id == R.id.drawer_fields) {
            fragment = new FieldsFragment();
            title = R.string.title_fields;
        } else if (id == R.id.drawer_market) {
            fragment = new MandiFragment();
            title = R.string.title_market;
        } else if (id == R.id.nav_weather) {
            fragment = new WeatherFragment();
            title = R.string.title_weather;
        } else if (id == R.id.nav_chat) {
            fragment = new ChatFragment();
            title = R.string.title_chat;
        } else if (id == R.id.drawer_encyclopedia) {
            fragment = new EncyclopediaFragment();
            title = R.string.title_encyclopedia;
        } else if (id == R.id.drawer_calendar) {
            fragment = new CropCalendarFragment();
            title = R.string.title_calendar;
        } else if (id == R.id.drawer_calculator) {
            fragment = new CalculatorFragment();
            title = R.string.title_calculator;
        } else if (id == R.id.drawer_field) {
            fragment = new FieldMapFragment();
            title = R.string.title_field;
        } else if (id == R.id.drawer_schemes) {
            fragment = new SchemesFragment();
            title = R.string.title_schemes;
        } else if (id == R.id.drawer_money) {
            fragment = new MoneyFragment();
            title = R.string.money_title;
        } else if (id == R.id.drawer_profile) {
            fragment = new ProfileFragment();
            title = R.string.profile_title;
        } else {
            fragment = new HomeFragment();
            title = R.string.app_name;
        }

        toolbar.setTitle(title);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    /** Home's tiles route through here. */
    public void openDestination(int menuId) {
        show(menuId);
        syncBottomNav(menuId);
    }

    private void showLanguagePicker() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(LocaleManager.nativeNames(),
                        LocaleManager.currentIndex(this),
                        (dialog, which) -> {
                            dialog.dismiss();
                            LocaleManager.set(this, LocaleManager.LANGUAGES[which][0]);
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showThemePicker() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.theme_title)
                .setSingleChoiceItems(AppTheme.labels(this), AppTheme.currentIndex(this),
                        (dialog, which) -> {
                            dialog.dismiss();
                            AppTheme.set(this, AppTheme.values()[which]);
                            recreate();   // the theme is chosen before inflation
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void confirmLogout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_log_out_title)
                .setMessage(R.string.confirm_log_out_message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_log_out,
                        (dialog, which) -> session.logOutTo(this, HomeActivity.class))
                .show();
    }

    /** Opens one saved field's detail screen. */
    /** Set on a crop task notification, so tapping it opens that field's crop journey. */
    public static final String EXTRA_JOURNEY_FIELD_ID = "journey_field_id";

    public void openCropJourney(int fieldId) {
        destination = R.id.nav_fields;
        com.mvx.agriculture.ui.CropJourneyFragment fragment = new com.mvx.agriculture.ui.CropJourneyFragment();
        Bundle args = new Bundle();
        args.putInt(com.mvx.agriculture.ui.CropJourneyFragment.ARG_FIELD_ID, fieldId);
        fragment.setArguments(args);
        toolbar.setTitle(R.string.journey_title);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        syncBottomNav(R.id.nav_fields);
    }

    @Override
    protected void onNewIntent(android.content.Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openFromNotification(intent);
    }

    private void openFromNotification(android.content.Intent intent) {
        int fieldId = intent == null ? 0 : intent.getIntExtra(EXTRA_JOURNEY_FIELD_ID, 0);
        if (fieldId > 0) {
            intent.removeExtra(EXTRA_JOURNEY_FIELD_ID);   // not again on rotation
            openCropJourney(fieldId);
        }
    }

    /** The map, zoomed to one saved field with its outline drawn. */
    public void openFieldOnMap(int fieldId) {
        destination = R.id.drawer_field;
        FieldMapFragment fragment = new FieldMapFragment();
        Bundle args = new Bundle();
        args.putInt(FieldMapFragment.ARG_FIELD_ID, fieldId);
        fragment.setArguments(args);
        toolbar.setTitle(R.string.title_field);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        syncBottomNav(R.id.nav_fields);
    }

    public void openFieldDetail(int fieldId) {
        destination = R.id.nav_fields;
        FieldDetailFragment fragment = new FieldDetailFragment();
        Bundle args = new Bundle();
        args.putInt(FieldDetailFragment.ARG_FIELD_ID, fieldId);
        fragment.setArguments(args);
        toolbar.setTitle(R.string.title_fields);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        syncBottomNav(R.id.nav_fields);
    }

    public void openMoney(int fieldId) {
        destination = R.id.drawer_money;
        MoneyFragment fragment = new MoneyFragment();
        if (fieldId > 0) {
            Bundle args = new Bundle();
            args.putInt(MoneyFragment.ARG_FILTER_FIELD_ID, fieldId);
            fragment.setArguments(args);
        }
        toolbar.setTitle(R.string.money_title);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        syncBottomNav(R.id.drawer_money);
    }

    /** Opens AgriBot pre-loaded with a question about a detected disease. */
    public void openChatAbout(String disease) {
        Bundle args = new Bundle();
        args.putString("disease", disease);
        openChatWith(args);
    }

    /** Opens AgriBot with an exact question already typed. */
    public void openChatWithPrefill(String prefill) {
        Bundle args = new Bundle();
        args.putString("prefill", prefill);
        openChatWith(args);
    }

    private void openChatWith(Bundle args) {
        destination = R.id.nav_chat;
        ChatFragment fragment = new ChatFragment();
        fragment.setArguments(args);
        toolbar.setTitle(R.string.title_chat);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
        syncBottomNav(R.id.nav_chat);
    }

    public String username() {
        return username;
    }

    public User currentUser() {
        return db.findUsers(username);
    }
}
