package com.mvx.agriculture.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Works out where a crop season stands: which tasks are done, due, overdue or
 * still to come, and which growth stage the crop is in.
 *
 * Dates are epoch days so there is no time-zone or clock arithmetic to get
 * wrong, and there are no Android types, so it is covered by JVM tests.
 */
public final class CropJourneyPlan {

    /** A task starts showing as due this many days before its date, so there is time to buy inputs. */
    public static final int DUE_LEAD_DAYS = 2;
    /** And stays due for a week before it counts as overdue. */
    public static final int DUE_GRACE_DAYS = 7;

    public enum Status {
        DONE, SKIPPED,
        /** Due date plus grace has passed and nothing was recorded. */
        OVERDUE,
        /** Within the lead and grace window around its date. */
        DUE,
        UPCOMING,
        /** Was already long past when the farmer started tracking; not nagged about. */
        EARLIER
    }

    /** What the farmer recorded for a task. */
    public static final class Record {
        public static final String DONE = "done";
        public static final String SKIPPED = "skipped";

        public final String state;
        public final long epochDay;

        public Record(String state, long epochDay) {
            this.state = state;
            this.epochDay = epochDay;
        }
    }

    public static final class Item {
        public final JourneyTemplate.TaskTemplate task;
        public final long dueEpochDay;
        public final int daysAfterPlanting;
        public final Status status;
        public final Record record;

        Item(JourneyTemplate.TaskTemplate task, long dueEpochDay, int daysAfterPlanting, Status status, Record record) {
            this.task = task;
            this.dueEpochDay = dueEpochDay;
            this.daysAfterPlanting = daysAfterPlanting;
            this.status = status;
            this.record = record;
        }
    }

    private CropJourneyPlan() {
    }

    /**
     * @param seedlingAge     days the crop had already grown before it was planted (0 for seed or setts)
     * @param trackingStarted the day the season was entered in the app; tasks long before it are
     *                        EARLIER rather than overdue, since the farmer may simply not have logged them
     */
    public static List<Item> plan(JourneyTemplate template, String methodId, int seedlingAge,
                                  long plantedEpochDay, long trackingStarted, long today,
                                  Map<String, Record> records) {
        List<Item> items = new ArrayList<>();
        for (JourneyTemplate.TaskTemplate task : template.tasks) {
            if (!task.methods.isEmpty() && !task.methods.contains(methodId)) {
                continue;
            }
            int dap = task.ageBasis ? task.day - seedlingAge : task.day;
            if (task.ageBasis && dap < 0) {
                continue;   // happened while the seedlings were still in the nursery
            }
            long due = plantedEpochDay + dap;
            Record record = records.get(task.id);
            items.add(new Item(task, due, dap, status(record, due, trackingStarted, today), record));
        }
        Collections.sort(items, (a, b) -> Long.compare(a.dueEpochDay, b.dueEpochDay));   // stable: keeps file order on ties
        return items;
    }

    private static Status status(Record record, long due, long trackingStarted, long today) {
        if (record != null) {
            return Record.SKIPPED.equals(record.state) ? Status.SKIPPED : Status.DONE;
        }
        if (due + DUE_GRACE_DAYS < trackingStarted) {
            return Status.EARLIER;
        }
        if (today > due + DUE_GRACE_DAYS) {
            return Status.OVERDUE;
        }
        if (today >= due - DUE_LEAD_DAYS) {
            return Status.DUE;
        }
        return Status.UPCOMING;
    }

    /** The growth stage on a given day, or null before planting or past the last stage. */
    public static JourneyTemplate.Stage stage(JourneyTemplate template, long plantedEpochDay, long today) {
        long dap = today - plantedEpochDay;
        if (dap < 0) {
            return null;
        }
        for (JourneyTemplate.Stage stage : template.stages) {
            if (dap >= stage.fromDay && dap <= stage.toDay) {
                return stage;
            }
        }
        return null;
    }
}
