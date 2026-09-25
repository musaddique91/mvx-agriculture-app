package com.mvx.agriculture.voice;

import java.util.HashMap;
import java.util.Map;

/**
 * Rewrites Urdu (Nastaliq/Arabic script) in Devanagari, so a Hindi voice can read
 * it when the phone has no Urdu voice at all.
 *
 * Spoken Urdu and Hindi are close enough that a farmer understands the result.
 * Urdu rarely writes short vowels, so the output is approximate ("کسان" comes out
 * as कसान rather than किसान) — far better than silence, not a replacement for a
 * real Urdu voice.
 */
public final class UrduScript {

    private static final Map<Character, String> CONSONANTS = new HashMap<>();
    private static final Map<String, String> ASPIRATED = new HashMap<>();

    static {
        String[][] consonants = {
                {"\u0628", "ब"}, {"\u067E", "प"}, {"\u062A", "त"}, {"\u0679", "ट"},
                {"\u062B", "स"}, {"\u062C", "ज"}, {"\u0686", "च"}, {"\u062D", "ह"},
                {"\u062E", "ख़"}, {"\u062F", "द"}, {"\u0688", "ड"}, {"\u0630", "ज़"},
                {"\u0631", "र"}, {"\u0691", "ड़"}, {"\u0632", "ज़"}, {"\u0698", "ज़"},
                {"\u0633", "स"}, {"\u0634", "श"}, {"\u0635", "स"}, {"\u0636", "ज़"},
                {"\u0637", "त"}, {"\u0638", "ज़"}, {"\u063A", "ग़"}, {"\u0641", "फ़"},
                {"\u0642", "क़"}, {"\u06A9", "क"}, {"\u0643", "क"}, {"\u06AF", "ग"},
                {"\u0644", "ल"}, {"\u0645", "म"}, {"\u0646", "न"}, {"\u06C1", "ह"},
                {"\u0647", "ह"}, {"\u06C3", "त"}, {"\u0629", "त"},
        };
        for (String[] row : consonants) {
            CONSONANTS.put(row[0].charAt(0), row[1]);
        }
        String[][] aspirated = {
                {"ब", "भ"}, {"प", "फ"}, {"त", "थ"}, {"ट", "ठ"}, {"ज", "झ"}, {"च", "छ"},
                {"द", "ध"}, {"ड", "ढ"}, {"क", "ख"}, {"ग", "घ"}, {"ड़", "ढ़"},
        };
        for (String[] row : aspirated) {
            ASPIRATED.put(row[0], row[1]);
        }
    }

    private enum Prev { START, CONSONANT, VOWEL }

    private UrduScript() {
    }

    /** True if the text has any Arabic-script letters worth converting. */
    public static boolean hasUrdu(String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\u0600' && c <= '\u06FF') {
                return true;
            }
        }
        return false;
    }

    public static String toDevanagari(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 8);
        Prev prev = Prev.START;
        int lastConsonantAt = -1;
        String lastConsonant = null;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : 0;

            String consonant = CONSONANTS.get(c);
            if (consonant != null) {
                lastConsonantAt = out.length();
                lastConsonant = consonant;
                out.append(consonant);
                prev = Prev.CONSONANT;
                continue;
            }

            switch (c) {
                case '\u06BE': // do-chashmi he: aspirates the consonant before it
                    if (prev == Prev.CONSONANT && ASPIRATED.containsKey(lastConsonant)) {
                        String aspirated = ASPIRATED.get(lastConsonant);
                        out.replace(lastConsonantAt, lastConsonantAt + lastConsonant.length(), aspirated);
                        lastConsonant = aspirated;
                    } else {
                        out.append('ह');
                        prev = Prev.CONSONANT;
                    }
                    break;

                case '\u0627': // alif
                case '\u0639': // ain, read like alif at a word's start
                    if (prev == Prev.START) {
                        if (next == '\u06CC' || next == '\u064A' || next == '\u06D2') {
                            out.append('ए');
                            i++;
                        } else if (next == '\u0648') {
                            out.append('ओ');
                            i++;
                        } else if (next == '\u0650') {
                            out.append('इ');
                            i++;
                        } else if (next == '\u064F') {
                            out.append('उ');
                            i++;
                        } else {
                            out.append('अ');
                        }
                        prev = Prev.VOWEL;
                    } else if (c == '\u0627') {
                        vowel(out, prev, "ा", "आ");
                        prev = Prev.VOWEL;
                    }
                    // A medial ain is silent.
                    break;

                case '\u0622': // alif madda
                    out.append(prev == Prev.CONSONANT ? "ा" : "आ");
                    prev = Prev.VOWEL;
                    break;

                case '\u0648': // vao: v at a word's start or before a vowel, else o
                case '\u0624':
                    if (c == '\u0624') {  // vao with hamza is always o
                        out.append('ओ');
                        prev = Prev.VOWEL;
                    } else if (prev == Prev.START || prev == Prev.VOWEL || next == '\u0627') {
                        lastConsonantAt = out.length();
                        lastConsonant = "व";
                        out.append('व');
                        prev = Prev.CONSONANT;
                    } else {
                        out.append('ो');
                        prev = Prev.VOWEL;
                    }
                    break;

                case '\u06CC': // choti ye: y at a word's start or before a vowel, else ee
                case '\u064A':
                    if (prev == Prev.START || prev == Prev.VOWEL
                            || next == '\u0627' || next == '\u0648') {
                        lastConsonantAt = out.length();
                        lastConsonant = "य";
                        out.append('य');
                        prev = Prev.CONSONANT;
                    } else {
                        out.append('ी');
                        prev = Prev.VOWEL;
                    }
                    break;

                case '\u06D2': // bari ye
                    vowel(out, prev, "े", "ए");
                    prev = Prev.VOWEL;
                    break;

                case '\u0626': // ye with hamza, carries the vowel after it
                    if (next == '\u06CC' || next == '\u064A') {
                        out.append('ई');
                        i++;
                    } else if (next == '\u06D2') {
                        out.append('ए');
                        i++;
                    } else {
                        out.append('इ');
                    }
                    prev = Prev.VOWEL;
                    break;

                case '\u06BA': // noon ghunna
                    out.append('ं');
                    break;

                case '\u0650': // zer
                    vowel(out, prev, "ि", "इ");
                    prev = Prev.VOWEL;
                    break;
                case '\u064F': // pesh
                    vowel(out, prev, "ु", "उ");
                    prev = Prev.VOWEL;
                    break;
                case '\u0670': // standing alif
                    vowel(out, prev, "ा", "आ");
                    prev = Prev.VOWEL;
                    break;
                case '\u064B': // tanween
                    out.append('न');
                    break;
                case '\u064E': // zabar: the inherent a already says it
                case '\u0651': // shadd
                case '\u0652': // sukun
                case '\u0621': // hamza
                case '\u0654': // hamza above
                case '\u200C':
                case '\u200D':
                    break;

                case '\u06D4':
                    out.append('।');
                    prev = Prev.START;
                    break;
                case '\u060C':
                    out.append(',');
                    prev = Prev.START;
                    break;
                case '\u061F':
                    out.append('?');
                    prev = Prev.START;
                    break;
                case '\u066A':
                    out.append('%');
                    prev = Prev.START;
                    break;

                default:
                    out.append(SpeechText.asciiDigits(String.valueOf(c)));
                    prev = Prev.START;
                    break;
            }
        }
        return out.toString();
    }

    /** A vowel sign after a consonant, the full letter anywhere else. */
    private static void vowel(StringBuilder out, Prev prev, String sign, String letter) {
        out.append(prev == Prev.CONSONANT ? sign : letter);
    }
}
