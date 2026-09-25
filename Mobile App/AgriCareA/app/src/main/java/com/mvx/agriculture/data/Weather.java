package com.mvx.agriculture.data;

import java.util.ArrayList;
import java.util.List;

/** One Open-Meteo answer, reduced to what the weather screen shows. */
public class Weather {

    public static class Day {
        public String date;
        public double maxC;
        public double minC;
        public double rainMm;
        public int rainChance;
        public double windKph;
    }

    public double nowC;
    public int humidity;
    public double windKph;
    public double rainTodayMm;
    public double soilTempC;
    public double soilMoisture;      // m³/m³, roughly 0.05 (dry) to 0.45 (saturated)
    public double rainNext6hMm;
    public double rainWeekMm;
    public final List<Day> days = new ArrayList<>();

    /** Wind above this carries spray off the target. */
    public boolean tooWindyToSpray() {
        return windKph > 15;
    }

    public boolean rainComing() {
        return rainNext6hMm >= 1.0;
    }

    /** Dry week and dry topsoil: the field will want water. */
    public boolean needsIrrigation() {
        return rainWeekMm < 10 && soilMoisture < 0.20;
    }

    public boolean heatStress() {
        for (Day day : days) {
            if (day.maxC >= 38) {
                return true;
            }
        }
        return false;
    }
}
