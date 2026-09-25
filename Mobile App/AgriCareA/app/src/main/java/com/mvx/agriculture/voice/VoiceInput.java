package com.mvx.agriculture.voice;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.google.android.material.textfield.TextInputLayout;
import com.mvx.agriculture.LocaleManager;
import com.mvx.agriculture.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

/**
 * Puts a microphone on every text box, so a farmer can speak a field's name, a
 * buyer, a note or an amount instead of typing it, in the app's language.
 *
 * The recogniser is launched through the activity, which must create this in
 * onCreate (result launchers can't be registered later) and implement
 * {@link Host} so dialogs opened from fragments can find it.
 */
public final class VoiceInput {

    /** An activity that owns a VoiceInput. */
    public interface Host {
        VoiceInput voiceInput();
    }

    /** Set as a TextInputLayout's tag to leave it alone (the chat box has its own mic). */
    public static final String TAG_NO_VOICE = "no_voice";

    private final Activity activity;
    private final ActivityResultLauncher<Intent> launcher;
    private WeakReference<EditText> target = new WeakReference<>(null);

    public VoiceInput(ComponentActivity activity) {
        this.activity = activity;
        this.launcher = activity.registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), result -> {
                    EditText edit = target.get();
                    target = new WeakReference<>(null);
                    if (edit == null || result.getResultCode() != Activity.RESULT_OK
                            || result.getData() == null) {
                        return;
                    }
                    ArrayList<String> heard =
                            result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (heard != null && !heard.isEmpty()) {
                        fill(edit, heard.get(0));
                    }
                });
    }

    /** Adds mics under {@code root} using the VoiceInput of the activity behind it. */
    public static void attachIn(View root) {
        if (root == null) {
            return;
        }
        Activity activity = Speaker.activityOf(root.getContext());
        if (activity instanceof Host) {
            ((Host) activity).voiceInput().attach(root);
        }
    }

    /** Adds a mic to each suitable text box under {@code root}. Safe to call twice. */
    public void attach(View root) {
        if (root instanceof TextInputLayout) {
            attachTo((TextInputLayout) root);
            return;
        }
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                attach(group.getChildAt(i));
            }
        }
    }

    private void attachTo(TextInputLayout layout) {
        EditText edit = layout.getEditText();
        if (edit == null
                || TAG_NO_VOICE.equals(layout.getTag())
                // Password eyes, dropdown arrows and date pickers keep their icon.
                || layout.getEndIconMode() != TextInputLayout.END_ICON_NONE
                || edit.getInputType() == InputType.TYPE_NULL
                || !edit.isEnabled()
                || ScreenReader.isPassword(edit)) {
            return;
        }
        layout.setEndIconMode(TextInputLayout.END_ICON_CUSTOM);
        layout.setEndIconDrawable(R.drawable.ic_mic);
        layout.setEndIconContentDescription(R.string.voice_type_hint);
        layout.setEndIconOnClickListener(v -> listen(edit, layout.getHint()));
    }

    /** Opens the recogniser for one box. */
    public void listen(EditText edit, CharSequence prompt) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, LocaleManager.speechTag(activity));
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, LocaleManager.speechTag(activity));
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, TextUtils.isEmpty(prompt)
                ? activity.getString(R.string.voice_speak_now) : prompt);
        // Don't talk over the farmer.
        Speaker.get(activity).stop();
        target = new WeakReference<>(edit);
        try {
            launcher.launch(intent);
        } catch (ActivityNotFoundException e) {
            target = new WeakReference<>(null);
            Toast.makeText(activity, R.string.speech_unsupported, Toast.LENGTH_SHORT).show();
        }
    }

    /** Puts what was heard into the box, shaped for the kind of box it is. */
    static void fill(EditText edit, String heard) {
        Context context = edit.getContext();
        int type = edit.getInputType();
        String value;
        if ((type & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER
                || (type & InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_PHONE) {
            boolean decimal = (type & InputType.TYPE_NUMBER_FLAG_DECIMAL) != 0;
            value = decimal ? SpeechText.number(heard) : SpeechText.wholeNumber(heard);
            if (value == null) {
                Toast.makeText(context, R.string.voice_no_number, Toast.LENGTH_SHORT).show();
                return;
            }
            edit.setText(value);
        } else {
            Editable current = edit.getText();
            boolean multiLine = (type & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
            // Long notes grow sentence by sentence; a single-line box is replaced.
            if (multiLine && current != null && current.toString().trim().length() > 0) {
                value = current.toString().trim() + " " + heard;
            } else {
                value = heard;
            }
            edit.setText(value);
        }
        edit.requestFocus();
        edit.setSelection(edit.getText() == null ? 0 : edit.getText().length());
    }
}
