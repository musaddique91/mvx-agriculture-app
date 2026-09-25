package com.mvx.agriculture.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CropJourneyPlanTest {

    private static final long PLANTED = 20_000;   // an epoch day

    private static JourneyTemplate.TaskTemplate task(String id, int day, boolean ageBasis, String... methods) {
        JourneyTemplate.TaskTemplate t = new JourneyTemplate.TaskTemplate();
        t.id = id;
        t.day = day;
        t.ageBasis = ageBasis;
        t.type = "care";
        t.title = id;
        t.methods = Arrays.asList(methods);
        return t;
    }

    private static JourneyTemplate.Stage stage(String id, int from, int to) {
        JourneyTemplate.Stage s = new JourneyTemplate.Stage();
        s.id = id;
        s.fromDay = from;
        s.toDay = to;
        return s;
    }

    private static JourneyTemplate cane() {
        JourneyTemplate t = new JourneyTemplate();
        t.crop = "Sugarcane";
        t.tasks = Arrays.asList(
                task("manure", -7, false, "setts", "seedlings"),
                task("sett_treatment", 0, false, "setts"),
                task("fertilizer_1", 30, false, "setts", "seedlings"),
                task("fertilizer_2", 60, false, "setts", "seedlings"),
                task("nursery_check", 20, true, "setts", "seedlings"),
                task("harvest", 330, true, "setts", "seedlings"));
        t.stages = Arrays.asList(stage("germination", 0, 35), stage("tillering", 36, 100));
        return t;
    }

    private static CropJourneyPlan.Item find(List<CropJourneyPlan.Item> items, String id) {
        for (CropJourneyPlan.Item item : items) {
            if (item.task.id.equals(id)) {
                return item;
            }
        }
        return null;
    }

    private static List<CropJourneyPlan.Item> plan(String method, int seedlingAge, long today,
                                                   Map<String, CropJourneyPlan.Record> records) {
        return CropJourneyPlan.plan(cane(), method, seedlingAge, PLANTED, PLANTED, today, records);
    }

    @Test
    public void tasksForAnotherPlantingMethodAreLeftOut() {
        assertTrue(find(plan("setts", 0, PLANTED, new HashMap<>()), "sett_treatment") != null);
        assertNull(find(plan("seedlings", 30, PLANTED, new HashMap<>()), "sett_treatment"));
    }

    @Test
    public void ageBasedTasksComeSoonerForSeedlingsRaisedElsewhere() {
        CropJourneyPlan.Item harvest = find(plan("seedlings", 30, PLANTED, new HashMap<>()), "harvest");
        assertEquals(PLANTED + 300, harvest.dueEpochDay);
        assertEquals(300, harvest.daysAfterPlanting);

        CropJourneyPlan.Item setts = find(plan("setts", 0, PLANTED, new HashMap<>()), "harvest");
        assertEquals(PLANTED + 330, setts.dueEpochDay);
    }

    @Test
    public void ageBasedTasksThatFellInTheNurseryAreLeftOut() {
        // nursery_check is at crop age 20; 30-day seedlings were already past it when planted.
        assertNull(find(plan("seedlings", 30, PLANTED, new HashMap<>()), "nursery_check"));
        assertTrue(find(plan("setts", 0, PLANTED, new HashMap<>()), "nursery_check") != null);
    }

    @Test
    public void aTaskIsUpcomingThenDueThenOverdue() {
        Map<String, CropJourneyPlan.Record> none = new HashMap<>();
        assertEquals(CropJourneyPlan.Status.UPCOMING, find(plan("setts", 0, PLANTED + 27, none), "fertilizer_1").status);
        assertEquals(CropJourneyPlan.Status.DUE, find(plan("setts", 0, PLANTED + 28, none), "fertilizer_1").status);
        assertEquals(CropJourneyPlan.Status.DUE, find(plan("setts", 0, PLANTED + 37, none), "fertilizer_1").status);
        assertEquals(CropJourneyPlan.Status.OVERDUE, find(plan("setts", 0, PLANTED + 38, none), "fertilizer_1").status);
    }

    @Test
    public void recordedTasksAreDoneOrSkippedWhateverTheDate() {
        Map<String, CropJourneyPlan.Record> records = new HashMap<>();
        records.put("fertilizer_1", new CropJourneyPlan.Record(CropJourneyPlan.Record.DONE, PLANTED + 31));
        records.put("fertilizer_2", new CropJourneyPlan.Record(CropJourneyPlan.Record.SKIPPED, PLANTED + 5));

        List<CropJourneyPlan.Item> items = plan("setts", 0, PLANTED + 200, records);
        assertEquals(CropJourneyPlan.Status.DONE, find(items, "fertilizer_1").status);
        assertEquals(CropJourneyPlan.Status.SKIPPED, find(items, "fertilizer_2").status);
    }

    @Test
    public void tasksLongPastWhenTrackingStartedAreEarlierNotOverdue() {
        // Planted 60 days before the farmer started using the app.
        long startedTracking = PLANTED + 60;
        List<CropJourneyPlan.Item> items = CropJourneyPlan.plan(cane(), "setts", 0, PLANTED,
                startedTracking, startedTracking, new HashMap<>());

        assertEquals(CropJourneyPlan.Status.EARLIER, find(items, "fertilizer_1").status);
        assertEquals(CropJourneyPlan.Status.DUE, find(items, "fertilizer_2").status);
        for (CropJourneyPlan.Item item : items) {
            assertFalse(item.task.id + " should not nag", item.status == CropJourneyPlan.Status.OVERDUE);
        }
    }

    @Test
    public void itemsAreInDueDateOrder() {
        List<CropJourneyPlan.Item> items = plan("setts", 0, PLANTED, new HashMap<>());
        for (int i = 1; i < items.size(); i++) {
            assertTrue(items.get(i - 1).dueEpochDay <= items.get(i).dueEpochDay);
        }
        assertEquals("manure", items.get(0).task.id);
    }

    @Test
    public void stageFollowsDaysAfterPlanting() {
        assertEquals("germination", CropJourneyPlan.stage(cane(), PLANTED, PLANTED + 10).id);
        assertEquals("tillering", CropJourneyPlan.stage(cane(), PLANTED, PLANTED + 50).id);
        assertNull("before planting", CropJourneyPlan.stage(cane(), PLANTED, PLANTED - 1));
    }
}
