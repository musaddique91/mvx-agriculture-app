package com.mvx.agriculture;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.appcompat.app.AppCompatActivity;

import com.mvx.agriculture.theme.AppTheme;
import com.mvx.agriculture.theme.SoftTheme;

import com.mvx.agriculture.auth.PasswordHasher;
import com.mvx.agriculture.auth.SessionManager;
import com.mvx.agriculture.database.DatabaseHelper;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class login extends AppCompatActivity implements com.mvx.agriculture.voice.VoiceInput.Host {

    private com.mvx.agriculture.voice.VoiceInput voiceInput;

    private DatabaseHelper database;
    private SessionManager session;
    private TextInputLayout usernameLayout, passwordLayout;
    private TextInputEditText username, password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(AppTheme.current(this).styleRes);
        super.onCreate(savedInstanceState);
        LocaleManager.applyFont(this);
        setContentView(R.layout.activity_login);
        SoftTheme.applyIfActive(findViewById(android.R.id.content));
        InsetsSupport.applyTo(this);
        voiceInput = new com.mvx.agriculture.voice.VoiceInput(this);
        voiceInput.attach(findViewById(android.R.id.content));

        database = new DatabaseHelper(this);
        session = new SessionManager(this);

        usernameLayout = findViewById(R.id.usernameLayout);
        passwordLayout = findViewById(R.id.passwordLayout);
        username = findViewById(R.id.username);
        password = findViewById(R.id.password);

        MaterialButton logIn = findViewById(R.id.btn);
        MaterialButton toSignUp = findViewById(R.id.link1);

        logIn.setOnClickListener(v -> attemptLogin());
        toSignUp.setOnClickListener(v -> {
            startActivity(new Intent(this, signup.class));
            finish();
        });
    }

    private void attemptLogin() {
        String user = text(username);
        String pass = text(password);

        usernameLayout.setError(null);
        passwordLayout.setError(null);

        if (TextUtils.isEmpty(user)) {
            usernameLayout.setError(getString(R.string.error_required));
            return;
        }
        if (TextUtils.isEmpty(pass)) {
            passwordLayout.setError(getString(R.string.error_required));
            return;
        }

        User account = database.findUsers(user);
        if (account == null || !PasswordHasher.verify(pass, account.getPwd())) {
            passwordLayout.setError(getString(R.string.error_bad_credentials));
            return;
        }

        // Accounts created before hashing existed still hold plaintext; upgrade on the
        // one occasion we have the password in hand.
        if (PasswordHasher.needsUpgrade(account.getPwd())) {
            database.updatePassword(account.getId(), PasswordHasher.hash(pass));
        }

        session.logIn(account.getEmail(), account.getCity(), account.getRegion());
        Intent intent = new Intent(this, MainShellActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private static String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }

    @Override
    public com.mvx.agriculture.voice.VoiceInput voiceInput() {
        return voiceInput;
    }
}
