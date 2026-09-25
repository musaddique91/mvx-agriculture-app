package com.mvx.agriculture.data;

/** One crop in one field, from planting until it is harvested. */
public class Season {
    public long id;
    public int fieldId;
    public String crop;
    /** A method id from the crop's journey, e.g. "setts" or "seedlings". */
    public String method;
    /** Days the crop had grown before planting; 0 for seed or setts. */
    public int seedlingAge;
    public long plantedDay;
    /** The day the season was entered in the app. */
    public long trackingStartDay;
    /** Null while the crop is still standing. */
    public Long endedDay;

    public boolean isActive() {
        return endedDay == null;
    }
}
