package com.mvx.agriculture.chat;

import com.mvx.agriculture.R;

/**
 * What the AI should look for in a photo.
 *
 * One vision model answers all four; only the instruction changes, which is why
 * adding a mode costs a prompt rather than a model.
 */
public enum ScanMode {

    DISEASE(R.string.mode_disease, R.drawable.ic_disease,
            "Identify the plant disease visible in this photo. Name the crop and the most "
                    + "likely disease, say how confident you are, list the visible symptoms you "
                    + "based that on, then give treatment steps naming active ingredients and "
                    + "dose per litre, and two prevention measures."),

    PEST(R.string.mode_pest, R.drawable.ic_pest,
            "Identify the insect pest or the pest damage visible in this photo. Name the pest "
                    + "and the crop, describe the damage you can see, then give control steps: "
                    + "cultural, biological, and chemical with active ingredients and dose per "
                    + "litre. Mention the economic threshold if there is a known one."),

    WEED(R.string.mode_weed, R.drawable.ic_weed,
            "Identify the weed in this photo. Give its common and botanical name, say whether it "
                    + "is grassy, broadleaf or sedge, explain how it competes with the crop, then "
                    + "give control options: manual, cultural, and herbicides with active "
                    + "ingredient, dose and the safe stage to apply."),

    NUTRIENT(R.string.mode_nutrient, R.drawable.ic_nutrient,
            "Look at this crop photo for nutrient deficiency symptoms. Say which nutrient is most "
                    + "likely short (N, P, K, or a micronutrient such as Fe, Zn, Mg, S or B) and "
                    + "what in the picture points to it, especially whether symptoms are on old or "
                    + "new leaves. Then give a correction: soil application and a foliar spray with "
                    + "dose per litre. Note anything else that mimics the same symptom.");

    public final int labelRes;
    public final int iconRes;
    public final String instruction;

    ScanMode(int labelRes, int iconRes, String instruction) {
        this.labelRes = labelRes;
        this.iconRes = iconRes;
        this.instruction = instruction;
    }
}
