package com.mvx.agriculture.data;

import java.util.ArrayList;
import java.util.List;

/**
 * One crop's journey from planting to harvest, as loaded from
 * assets/crop_journeys.json. Plain fields and no Android types, so the plan
 * built from it is covered by JVM tests.
 */
public class JourneyTemplate {

    public String crop;
    public List<Source> sources = new ArrayList<>();
    public List<Method> methods = new ArrayList<>();
    public int harvestAgeDays;
    public List<Stage> stages = new ArrayList<>();
    public List<TaskTemplate> tasks = new ArrayList<>();

    /** Where the timings and doses come from, shown to the farmer. */
    public static class Source {
        public String title;
        public String url;
    }

    /** How the crop goes into the field, e.g. setts or 30-day seedlings. */
    public static class Method {
        public String id;
        public int seedlingAgeDays;
    }

    /** A growth phase, with how to water during it. */
    public static class Stage {
        public String id;
        public String name;
        public String water;
        public int fromDay;
        public int toDay;
    }

    public static class TaskTemplate {
        public String id;
        /** manure, fertilizer, seed, weeding, care, pest_watch, pest_control, goli, harvest */
        public String type;
        public String title;
        public String detail;
        /** Days after planting in the field, or the crop's total age when {@link #ageBasis}. */
        public int day;
        public boolean ageBasis;
        /** Only needed when a condition is met, e.g. pest damage above a threshold. */
        public boolean optional;
        public List<String> methods = new ArrayList<>();
    }

    public Method method(String id) {
        for (Method method : methods) {
            if (method.id.equals(id)) {
                return method;
            }
        }
        return methods.isEmpty() ? null : methods.get(0);
    }
}
