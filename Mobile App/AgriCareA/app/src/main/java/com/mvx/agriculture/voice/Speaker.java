package com.mvx.agriculture.voice;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mvx.agriculture.LocaleManager;
import com.mvx.agriculture.R;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Reads text aloud in the app's language, for farmers who find reading hard.
 *
 * One engine serves the whole app: starting a text-to-speech engine takes a moment
 * and holds a service connection, so screens share this instead of each owning one.
 * Text asked for before the engine is ready waits and is spoken once it is.
 */
public final class Speaker {

    /** Told on the main thread whenever speech starts or stops, to flip play/stop icons. */
    public interface Listener {
        void onSpeakingChanged(boolean speaking);
    }

    private static Speaker instance;

    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final CopyOnWriteArraySet<Listener> listeners = new CopyOnWriteArraySet<>();
    private TextToSpeech tts;
    private boolean ready;
    private boolean failed;
    private String pending;
    private WeakReference<Activity> requester = new WeakReference<>(null);
    private String languageInForce;
    private String lastUtteranceId;
    private boolean speaking;
    private int generation;

    private Speaker(Context context) {
        app = context.getApplicationContext();
    }

    public static synchronized Speaker get(Context context) {
        if (instance == null) {
            instance = new Speaker(context);
        }
        return instance;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean isSpeaking() {
        return speaking;
    }

    /**
     * Speaks the text, replacing anything already being read. The context is used to
     * offer a voice download if the phone has none for the app's language.
     */
    public void speak(Context context, CharSequence text) {
        String clean = SpeechText.clean(text == null ? null : text.toString());
        if (TextUtils.isEmpty(clean)) {
            return;
        }
        Activity activity = activityOf(context);
        if (activity != null) {
            requester = new WeakReference<>(activity);
        }
        if (failed) {
            // The engine may have been installed since; try again from scratch.
            failed = false;
            if (tts != null) {
                tts.shutdown();
            }
            tts = null;
        }
        if (tts == null) {
            pending = clean;
            tts = new TextToSpeech(app, this::onInit);
            return;
        }
        if (!ready) {
            pending = clean;
            return;
        }
        say(clean);
    }

    /** Speaks if silent, stops if talking — the behaviour of every "listen" button. */
    public void toggle(Context context, CharSequence text) {
        if (speaking) {
            stop();
        } else {
            speak(context, text);
        }
    }

    public void stop() {
        pending = null;
        if (tts != null && ready) {
            tts.stop();
        }
        setSpeaking(false);
    }

    private void onInit(int status) {
        if (status != TextToSpeech.SUCCESS) {
            failed = true;
            pending = null;
            main.post(() -> Toast.makeText(app, R.string.voice_no_engine, Toast.LENGTH_LONG).show());
            return;
        }
        ready = true;
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                main.post(() -> setSpeaking(true));
            }

            @Override
            public void onDone(String utteranceId) {
                main.post(() -> {
                    if (utteranceId.equals(lastUtteranceId)) {
                        setSpeaking(false);
                    }
                });
            }

            @Override
            @SuppressWarnings("deprecation")
            public void onError(String utteranceId) {
                main.post(() -> {
                    if (utteranceId.equals(lastUtteranceId)) {
                        setSpeaking(false);
                    }
                });
            }

            @Override
            public void onStop(String utteranceId, boolean interrupted) {
                main.post(() -> {
                    if (utteranceId.equals(lastUtteranceId)) {
                        setSpeaking(false);
                    }
                });
            }
        });
        String text = pending;
        pending = null;
        if (text != null) {
            main.post(() -> say(text));
        }
    }

    private void say(String text) {
        if (!applyLanguage()) {
            offerVoiceDownload();
            return;
        }
        tts.setSpeechRate(VoicePrefs.rate(app));
        tts.stop();
        generation++;
        List<String> pieces = SpeechText.chunks(text,
                Math.max(200, TextToSpeech.getMaxSpeechInputLength() - 100));
        for (int i = 0; i < pieces.size(); i++) {
            String id = "agri-" + generation + "-" + i;
            lastUtteranceId = id;
            tts.speak(pieces.get(i),
                    i == 0 ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, id);
        }
        setSpeaking(true);
    }

    /** Picks a voice for the app's language. False when the phone has none. */
    private boolean applyLanguage() {
        String tag = LocaleManager.currentTag(app);
        if (tag.equals(languageInForce)) {
            return true;
        }
        for (Locale candidate : candidates(tag)) {
            int result = tts.isLanguageAvailable(candidate);
            if (result >= TextToSpeech.LANG_AVAILABLE) {
                tts.setLanguage(candidate);
                languageInForce = tag;
                return true;
            }
        }
        languageInForce = null;
        return false;
    }

    private static Locale[] candidates(String tag) {
        switch (tag) {
            case "ur":
                // Most engines ship Urdu as the Pakistani voice only.
                return new Locale[]{new Locale("ur", "IN"), new Locale("ur", "PK"), new Locale("ur")};
            case "en":
                return new Locale[]{new Locale("en", "IN"), Locale.UK, Locale.US};
            default:
                return new Locale[]{new Locale(tag, "IN"), new Locale(tag)};
        }
    }

    private void offerVoiceDownload() {
        Activity activity = requester.get();
        if (activity == null || activity.isFinishing()) {
            Toast.makeText(app, R.string.voice_missing_title, Toast.LENGTH_LONG).show();
            return;
        }
        new MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.voice_missing_title)
                .setMessage(activity.getString(R.string.voice_missing_message,
                        LocaleManager.nativeNames()[LocaleManager.currentIndex(activity)]))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.voice_install, (d, w) -> {
                    try {
                        activity.startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
                    } catch (ActivityNotFoundException e) {
                        Toast.makeText(activity, R.string.voice_no_engine, Toast.LENGTH_LONG).show();
                    }
                })
                .show();
    }

    private void setSpeaking(boolean now) {
        if (speaking == now) {
            return;
        }
        speaking = now;
        for (Listener listener : listeners) {
            listener.onSpeakingChanged(now);
        }
    }

    static Activity activityOf(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }
}
