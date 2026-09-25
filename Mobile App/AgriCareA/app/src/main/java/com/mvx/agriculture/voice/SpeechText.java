package com.mvx.agriculture.voice;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text shaping for the voice features, kept free of Android so it can be tested
 * on the JVM.
 */
public final class SpeechText {

    /** Sentence ends in the scripts the app speaks: Latin, Devanagari danda, Urdu. */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[.!?\u0964\u06D4\u061F\n])");
    private static final Pattern GROUPED = Pattern.compile("\\d{1,3}(?:,\\d{2,3})*,\\d{3}(?![\\d,])");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");

    private SpeechText() {
    }

    /**
     * Strips the markdown AgriBot likes to answer in, so the voice doesn't read out
     * "star star" or "hash".
     */
    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        String out = text
                .replaceAll("(?m)^\\s*#{1,6}\\s*", "")          // headings
                .replaceAll("(?m)^\\s*[-*•]\\s+", "")       // bullets
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replaceAll("(?<!\\w)\\*(?!\\s)|(?<!\\s)\\*(?!\\w)", "")
                .replaceAll("[ \\t]+", " ");
        return out.trim();
    }

    /**
     * Splits text into pieces no longer than {@code max}, breaking at sentence ends
     * where possible, then at spaces, and only mid-word as a last resort.
     */
    public static List<String> chunks(String text, int max) {
        List<String> out = new ArrayList<>();
        if (text == null || max <= 0) {
            return out;
        }
        StringBuilder current = new StringBuilder();
        for (String sentence : SENTENCE_END.split(text)) {
            String piece = sentence.trim();
            if (piece.isEmpty()) {
                continue;
            }
            if (current.length() > 0 && current.length() + 1 + piece.length() > max) {
                out.add(current.toString());
                current.setLength(0);
            }
            while (piece.length() > max) {
                int cut = piece.lastIndexOf(' ', max);
                if (cut <= 0) {
                    cut = max;
                }
                out.add(piece.substring(0, cut).trim());
                piece = piece.substring(cut).trim();
            }
            if (piece.isEmpty()) {
                continue;
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(piece);
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    /**
     * Folds Devanagari, Kannada and Arabic-Indic digits into 0-9, since recognisers
     * for Hindi, Marathi, Kannada and Urdu may answer "५००" or "۵۰۰" for 500.
     */
    public static String asciiDigits(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int value = -1;
            if (c >= '\u0966' && c <= '\u096F') {        // Devanagari
                value = c - '\u0966';
            } else if (c >= '\u0CE6' && c <= '\u0CEF') { // Kannada
                value = c - '\u0CE6';
            } else if (c >= '\u0660' && c <= '\u0669') { // Arabic-Indic
                value = c - '\u0660';
            } else if (c >= '\u06F0' && c <= '\u06F9') { // Extended Arabic-Indic (Urdu)
                value = c - '\u06F0';
            }
            out.append(value >= 0 ? (char) ('0' + value) : c);
        }
        return out.toString();
    }

    /**
     * The number in a spoken phrase, for number-only fields: "1,500 rupees" gives
     * "1500", "२.५" gives "2.5". Returns null when nothing numeric was heard.
     */
    public static String number(String spoken) {
        String text = asciiDigits(spoken);
        // "1,500" and the Indian "1,20,000" are digit groups; "2,5" is a decimal comma.
        Matcher grouped = GROUPED.matcher(text);
        StringBuffer ungrouped = new StringBuffer();
        while (grouped.find()) {
            grouped.appendReplacement(ungrouped, grouped.group().replace(",", ""));
        }
        grouped.appendTail(ungrouped);
        Matcher m = NUMBER.matcher(ungrouped);
        if (!m.find()) {
            return null;
        }
        return m.group().replace(',', '.');
    }

    /** Whole numbers only, for fields that reject a decimal point. */
    public static String wholeNumber(String spoken) {
        String n = number(spoken);
        if (n == null) {
            return null;
        }
        int dot = n.indexOf('.');
        return dot < 0 ? n : n.substring(0, dot);
    }

    /**
     * Joins screen texts into one script, giving each a sentence end so the voice
     * pauses between a heading and the line under it. Repeats in a row are dropped.
     */
    public static String joinSentences(List<String> parts) {
        StringBuilder out = new StringBuilder();
        String previous = null;
        for (String raw : parts) {
            String part = raw == null ? "" : raw.trim();
            if (part.isEmpty() || part.equals(previous)) {
                continue;
            }
            previous = part;
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(part);
            char last = part.charAt(part.length() - 1);
            if (".!?:\u0964\u06D4\u061F".indexOf(last) < 0) {
                out.append('.');
            }
        }
        return out.toString();
    }

    /** "Label: value", skipping whichever half is empty. */
    public static String labelled(CharSequence label, CharSequence value) {
        String l = label == null ? "" : label.toString().trim();
        String v = value == null ? "" : value.toString().trim();
        if (l.isEmpty()) {
            return v;
        }
        if (v.isEmpty()) {
            return l;
        }
        return l + ": " + v;
    }
}
