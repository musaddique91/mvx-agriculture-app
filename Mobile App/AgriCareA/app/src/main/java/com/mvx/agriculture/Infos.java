package com.mvx.agriculture;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.mvx.agriculture.theme.AppTheme;
import com.mvx.agriculture.theme.SoftTheme;

import com.google.android.material.appbar.MaterialToolbar;

/** What the app does, reachable from the dashboard overflow menu. */
public class Infos extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(AppTheme.current(this).styleRes);
        super.onCreate(savedInstanceState);
        LocaleManager.applyFont(this);
        setContentView(R.layout.activity_infos);
        SoftTheme.applyIfActive(findViewById(android.R.id.content));
        InsetsSupport.applyTo(this);

        ((MaterialToolbar) findViewById(R.id.toolbar)).setNavigationOnClickListener(v -> finish());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}
