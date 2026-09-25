package com.mvx.agriculture.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class SpeechTextTest {

    @Test
    public void cleanStripsMarkdown() {
        assertEquals("Early blight\nSpray copper fungicide\nRemove bad leaves",
                SpeechText.clean("## Early blight\n- **Spray** copper fungicide\n* Remove *bad* leaves"));
    }

    @Test
    public void cleanKeepsArithmeticStar() {
        assertEquals("5 * 3 kg", SpeechText.clean("5 * 3 kg"));
    }

    @Test
    public void cleanHandlesNull() {
        assertEquals("", SpeechText.clean(null));
    }

    @Test
    public void chunksBreakAtSentencesAndRespectMax() {
        List<String> pieces = SpeechText.chunks("One two. Three four five. Six.", 15);
        assertEquals(Arrays.asList("One two.", "Three four", "five. Six."), pieces);
        for (String piece : pieces) {
            assertTrue(piece.length() <= 15);
        }
    }

    @Test
    public void chunksSplitOnDanda() {
        assertEquals(Arrays.asList("पानी दें।", "खाद डालें।"),
                SpeechText.chunks("पानी दें। खाद डालें।", 10));
    }

    @Test
    public void chunksKeepShortTextWhole() {
        assertEquals(Arrays.asList("Hello there. Bye."), SpeechText.chunks("Hello there. Bye.", 100));
    }

    @Test
    public void asciiDigitsFoldsIndianScripts() {
        assertEquals("500", SpeechText.asciiDigits("५००"));    // Devanagari
        assertEquals("42", SpeechText.asciiDigits("೪೨"));       // Kannada
        assertEquals("75", SpeechText.asciiDigits("۷۵"));       // Urdu
        assertEquals("19", SpeechText.asciiDigits("١٩"));       // Arabic-Indic
    }

    @Test
    public void numberExtractsFromSpeech() {
        assertEquals("1500", SpeechText.number("1,500 rupees"));
        assertEquals("250", SpeechText.number("२५० रुपये"));
        assertEquals("2.5", SpeechText.number("2.5 quintal"));
        assertEquals("2.5", SpeechText.number("2,5"));
        assertEquals("120000", SpeechText.number("1,20,000"));
        assertNull(SpeechText.number("paanch sau"));
    }

    @Test
    public void wholeNumberDropsDecimals() {
        assertEquals("30", SpeechText.wholeNumber("30.5 days"));
        assertEquals("45", SpeechText.wholeNumber("45 दिन"));
    }

    @Test
    public void joinSentencesAddsStopsAndDropsRepeats() {
        assertEquals("Weather.\nRain today!\nHumidity: 80%.",
                SpeechText.joinSentences(Arrays.asList("Weather", "Weather", " ", "Rain today!", "Humidity: 80%")));
        assertEquals("मौसम।\nबारिश।", SpeechText.joinSentences(Arrays.asList("मौसम।", "बारिश।")));
    }

    @Test
    public void labelledSkipsEmptyHalves() {
        assertEquals("Amount: 500", SpeechText.labelled("Amount", "500"));
        assertEquals("Amount", SpeechText.labelled("Amount", ""));
        assertEquals("500", SpeechText.labelled(null, "500"));
    }
}
