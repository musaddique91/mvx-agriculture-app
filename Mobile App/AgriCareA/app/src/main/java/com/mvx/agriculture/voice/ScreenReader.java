package com.mvx.agriculture.voice;

import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns whatever a screen shows into one script to read aloud, top to bottom.
 *
 * Working from the live view tree rather than per-screen code means every screen —
 * weather, mandi prices, schemes, a field's crop journey — can be heard without
 * each one having to say what matters on it.
 */
public final class ScreenReader {

    private ScreenReader() {
    }

    /** The visible text under {@code root}, headed by {@code title} if given. */
    public static String read(CharSequence title, View... roots) {
        List<String> parts = new ArrayList<>();
        if (title != null) {
            parts.add(title.toString());
        }
        for (View root : roots) {
            collect(root, parts);
        }
        return SpeechText.joinSentences(parts);
    }

    private static void collect(View view, List<String> out) {
        if (view == null || view.getVisibility() != View.VISIBLE
                || view.getImportantForAccessibility()
                == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS) {
            return;
        }

        if (view instanceof TextInputLayout) {
            // Read the field as "label: what's typed", never its inner views separately.
            TextInputLayout layout = (TextInputLayout) view;
            EditText edit = layout.getEditText();
            out.add(SpeechText.labelled(layout.getHint(), edit == null || isPassword(edit)
                    ? null : edit.getText()));
            return;
        }
        if (view instanceof EditText) {
            EditText edit = (EditText) view;
            out.add(SpeechText.labelled(edit.getHint(), isPassword(edit) ? null : edit.getText()));
            return;
        }
        if (view instanceof TextView) {
            CharSequence text = ((TextView) view).getText();
            if (text != null && text.length() > 0) {
                out.add(text.toString());
            }
            return;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collect(group.getChildAt(i), out);
            }
        }
    }

    static boolean isPassword(EditText edit) {
        int variation = edit.getInputType() & InputType.TYPE_MASK_VARIATION;
        return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                || variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }
}
