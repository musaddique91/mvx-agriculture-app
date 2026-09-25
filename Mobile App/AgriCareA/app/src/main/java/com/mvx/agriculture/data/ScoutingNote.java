package com.mvx.agriculture.data;

import com.mvx.agriculture.R;

/** A geotagged observation dropped while walking a field. */
public class ScoutingNote {

    /** The preset issue types, so notes stay searchable and comparable. */
    public enum Category {
        PEST(R.string.note_pest, R.color.accent_market),
        DISEASE(R.string.note_disease, R.color.md_error),
        WEED(R.string.note_weed, R.color.accent_book),
        NUTRIENT(R.string.note_nutrient, R.color.accent_weather),
        IRRIGATION(R.string.note_irrigation, R.color.accent_chat),
        OTHER(R.string.note_other, R.color.accent_calc);

        public final int labelRes;
        public final int colorRes;

        Category(int labelRes, int colorRes) {
            this.labelRes = labelRes;
            this.colorRes = colorRes;
        }
    }

    public int id;
    public int fieldId;
    public double lat;
    public double lon;
    public Category category = Category.OTHER;
    public String text;
    public String photoPath;
    public long createdAt;
}
