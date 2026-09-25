package com.mvx.agriculture.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UrduScriptTest {

    private static void check(String urdu, String devanagari) {
        assertEquals(devanagari, UrduScript.toDevanagari(urdu));
    }

    @Test
    public void commonFarmWords() {
        check("پانی", "पानी");
        check("زمین", "ज़मीन");
        check("بارش", "बारश");
        check("فصل", "फ़सल");
        check("کسان", "कसान");
    }

    @Test
    public void aspirationJoinsThePreviousConsonant() {
        check("بھارت", "भारत");
        check("دودھ", "दोध");
        check("کھاد", "खाद");
    }

    @Test
    public void wordInitialVowels() {
        check("ایک", "एक");
        check("اور", "ओर");
        check("آج", "आज");
        check("اب", "अब");
    }

    @Test
    public void vaoAndYeActAsConsonantsBeforeAVowel() {
        check("جواب", "जवाब");
        check("کو", "को");
        check("پیار", "पयार");
        check("یہ", "यह");
    }

    @Test
    public void hamzaCarriesTheVowel() {
        check("آئے", "आए");
        check("کئی", "कई");
    }

    @Test
    public void diacriticsAndNasal() {
        check("کِسان", "किसान");
        check("میں", "मीं");
    }

    @Test
    public void punctuationDigitsAndLatinPassThrough() {
        check("۵۰۰ روپے۔", "500 रोपे।");
        check("pH ۶", "pH 6");
        check("کیا؟", "कया?");
    }

    @Test
    public void detectsUrdu() {
        assertTrue(UrduScript.hasUrdu("موسم 30°"));
        assertFalse(UrduScript.hasUrdu("Weather 30°"));
        assertFalse(UrduScript.hasUrdu(null));
    }
}
