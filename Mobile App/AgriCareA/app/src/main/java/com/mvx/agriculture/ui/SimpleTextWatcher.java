package com.mvx.agriculture.ui;

import android.text.Editable;
import android.text.TextWatcher;

/** TextWatcher boilerplate reduced to the one callback these screens use. */
class SimpleTextWatcher implements TextWatcher {

    interface OnChanged {
        void onChanged(String text);
    }

    private final OnChanged callback;

    SimpleTextWatcher(OnChanged callback) {
        this.callback = callback;
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        callback.onChanged(s.toString());
    }

    @Override
    public void afterTextChanged(Editable s) {
    }
}
