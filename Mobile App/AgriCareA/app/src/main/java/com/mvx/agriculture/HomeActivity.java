package com.mvx.agriculture;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.mvx.agriculture.theme.AppTheme;
import com.mvx.agriculture.theme.SoftTheme;

import com.mvx.agriculture.auth.SessionManager;
import com.google.android.material.button.MaterialButton;

/** Welcome screen. Skips straight to the dashboard when a valid session exists. */
public class HomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(AppTheme.current(this).styleRes);
        super.onCreate(savedInstanceState);
        LocaleManager.applyFont(this);

        SessionManager session = new SessionManager(this);
        if (session.isLoggedIn()) {
            startActivity(new Intent(this, MainShellActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_home);
        SoftTheme.applyIfActive(findViewById(android.R.id.content));
        InsetsSupport.applyTo(this, R.id.heroContent);

        MaterialButton signUp = findViewById(R.id.btnSignIn);
        MaterialButton logIn = findViewById(R.id.btnLogIn);

        signUp.setOnClickListener(v -> startActivity(new Intent(this, signup.class)));
        logIn.setOnClickListener(v -> startActivity(new Intent(this, login.class)));
    }
}
