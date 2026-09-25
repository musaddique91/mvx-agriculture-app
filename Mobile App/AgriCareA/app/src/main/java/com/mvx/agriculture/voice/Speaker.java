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
import android.speech.tts.Voice;
import android.text.TextUtils;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mvx.agriculture.LocaleManager;
import com.mvx.agriculture.R;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Reads text aloud in the app's language, for farmers who find reading hard.
 *
 * One engine serves the whole app: starting a text-to-speech engine takes a moment
 * and holds a service connection, so screens share this instead of each owning one.
 * Text asked for before the engine is ready waits and is spoken once it is.
 *
 * Phones often ship a maker's engine as the default (Samsung's has no Urdu or
 * Kannada) with Google's installed beside it, so when the default engine lacks the
 * language every other installed engine is tried before giving up. Urdu has one
 * more fallback: spoken Urdu and Hindi are close, so with no Urdu voice anywhere the
 * text is rewritten in Devanagari and read by a Hindi voice.
 */
public final class Speaker {

    /** Told on the main thread whenever speech starts or stops, to flip play/stop icons. */
    public interface Listener {
        void onSpeakingChanged(boolean speaking);
    }

    private static final String GOOGLE_TTS = "com.google.android.tts";

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

    /** Engine package in use; null means the phone's default. */
    private String engine;
    /** The app language the engines below were searched for. */
    private String searchedFor;
    private final Set<String> enginesTried = new HashSet<>();
    /** Urdu with no Urdu voice anywhere: read it with a Hindi voice instead. */
    private boolean hindiForUrdu;
    private boolean toldAboutFallback;

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
            engine = null;
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
        if (status != TextToSpeech.SUCCESS && engine != null) {
            // That engine wouldn't start; carry on down the list.
            enginesTried.add(engine);
            String text = pending;
            pending = null;
            main.post(() -> {
                String nextEngine = nextEngine();
                if (text != null && nextEngine != null) {
                    switchEngine(nextEngine, text);
                } else {
                    failed = true;
                }
            });
            return;
        }
        if (status != TextToSpeech.SUCCESS) {
            failed = true;
            pending = null;
            main.post(() -> Toast.makeText(app, R.string.voice_no_engine, Toast.LENGTH_LONG).show());
            return;
        }
        ready = true;
        if (engine == null) {
            engine = tts.getDefaultEngine();
        }
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
                onError(utteranceId, TextToSpeech.ERROR);
            }

            @Override
            public void onError(String utteranceId, int errorCode) {
                main.post(() -> {
                    if (!utteranceId.equals(lastUtteranceId)) {
                        return;
                    }
                    setSpeaking(false);
                    // A voice that needs the internet or isn't downloaded fails quietly
                    // otherwise; say why, and offer the download that fixes it for good.
                    if (errorCode == TextToSpeech.ERROR_NETWORK
                            || errorCode == TextToSpeech.ERROR_NETWORK_TIMEOUT
                            || errorCode == TextToSpeech.ERROR_NOT_INSTALLED_YET) {
                        languageInForce = null;
                        offerVoiceDownload();
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
        String tag = LocaleManager.currentTag(app);
        if (!tag.equals(searchedFor)) {
            // New language: every engine gets a fresh chance.
            searchedFor = tag;
            enginesTried.clear();
            hindiForUrdu = false;
            languageInForce = null;
        }
        String want = hindiForUrdu ? "hi" : tag;
        if (applyLanguage(want)) {
            if (hindiForUrdu && !toldAboutFallback) {
                toldAboutFallback = true;
                Toast.makeText(app, R.string.voice_urdu_fallback, Toast.LENGTH_LONG).show();
            }
            speakNow(hindiForUrdu && UrduScript.hasUrdu(text) ? UrduScript.toDevanagari(text) : text);
            return;
        }

        // This engine can't do it; try the next one installed.
        enginesTried.add(engine);
        String nextEngine = nextEngine();
        if (nextEngine != null) {
            switchEngine(nextEngine, text);
            return;
        }
        if ("ur".equals(tag) && !hindiForUrdu) {
            hindiForUrdu = true;
            enginesTried.clear();
            say(text);
            return;
        }
        offerVoiceDownload();
    }

    private void speakNow(String text) {
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

    /** Installed engines not yet tried for this language, Google's first. */
    private String nextEngine() {
        if (tts == null) {
            return null;
        }
        List<String> order = new ArrayList<>();
        for (TextToSpeech.EngineInfo info : tts.getEngines()) {
            if (GOOGLE_TTS.equals(info.name)) {
                order.add(0, info.name);
            } else {
                order.add(info.name);
            }
        }
        for (String name : order) {
            if (!enginesTried.contains(name)) {
                return name;
            }
        }
        return null;
    }

    private void switchEngine(String name, String text) {
        if (tts != null) {
            tts.shutdown();
        }
        ready = false;
        languageInForce = null;
        engine = name;
        pending = text;
        tts = new TextToSpeech(app, this::onInit, name);
    }

    /**
     * Picks a voice for the language on the current engine. False when it has none
     * that can speak now: missing entirely, or listed but never downloaded.
     */
    private boolean applyLanguage(String tag) {
        if (tag.equals(languageInForce)) {
            return true;
        }
        for (Locale candidate : candidates(tag)) {
            if (tts.isLanguageAvailable(candidate) < TextToSpeech.LANG_AVAILABLE) {
                continue;
            }
            Voice voice = bestVoice(candidate);
            if (voice != null) {
                if (tts.setVoice(voice) == TextToSpeech.SUCCESS) {
                    languageInForce = tag;
                    return true;
                }
            } else if (!hasVoiceList() && tts.setLanguage(candidate) >= TextToSpeech.LANG_AVAILABLE) {
                // Older engines don't list voices; trust the language check.
                languageInForce = tag;
                return true;
            }
        }
        languageInForce = null;
        return false;
    }

    private boolean hasVoiceList() {
        try {
            Set<Voice> voices = tts.getVoices();
            return voices != null && !voices.isEmpty();
        } catch (RuntimeException e) {
            return false;   // some engines throw here
        }
    }

    /**
     * The best installed voice for the locale's language: downloaded voices first
     * (they work offline in the field), then network ones; the locale's own region
     * before others.
     */
    private Voice bestVoice(Locale locale) {
        Set<Voice> voices;
        try {
            voices = tts.getVoices();
        } catch (RuntimeException e) {
            return null;
        }
        if (voices == null) {
            return null;
        }
        Voice best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Voice voice : voices) {
            Locale vl = voice.getLocale();
            if (vl == null || !locale.getLanguage().equals(vl.getLanguage())) {
                continue;
            }
            Set<String> features = voice.getFeatures();
            if (features != null && features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)) {
                continue;   // listed, but speaking would fail until it is downloaded
            }
            int score = voice.getQuality();
            if (!voice.isNetworkConnectionRequired()) {
                score += 1000;
            }
            if (locale.getCountry().equals(vl.getCountry())) {
                score += 100;
            }
            if (score > bestScore) {
                bestScore = score;
                best = voice;
            }
        }
        return best;
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

    /** Forget which engines failed, e.g. after the farmer has installed a voice. */
    public void recheckVoices() {
        searchedFor = null;
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
                .setPositiveButton(R.string.voice_install, (d, w) -> openVoiceDownload(activity))
                .show();
    }

    /**
     * Opens the voice download screen of Google's engine — the one with Urdu, Kannada
     * and Marathi — or its Play Store page if it isn't installed.
     */
    public void openVoiceDownload(Activity activity) {
        recheckVoices();
        boolean hasGoogle = false;
        if (tts != null) {
            for (TextToSpeech.EngineInfo info : tts.getEngines()) {
                hasGoogle |= GOOGLE_TTS.equals(info.name);
            }
        }
        try {
            if (hasGoogle) {
                activity.startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                        .setPackage(GOOGLE_TTS));
                return;
            }
        } catch (ActivityNotFoundException ignored) {
            // fall through to the store
        }
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW,
                    android.net.Uri.parse("market://details?id=" + GOOGLE_TTS)));
        } catch (ActivityNotFoundException e) {
            try {
                activity.startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=" + GOOGLE_TTS)));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(activity, R.string.voice_no_engine, Toast.LENGTH_LONG).show();
            }
        }
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
