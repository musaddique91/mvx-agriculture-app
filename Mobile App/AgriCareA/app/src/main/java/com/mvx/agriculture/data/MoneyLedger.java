package com.mvx.agriculture.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adds up what a field's crop season cost and earned. Each line is one field's
 * share of one entry. Pure Java; covered by JVM tests.
 */
public final class MoneyLedger {

    public static final String SPENT = "spent";
    public static final String RECEIVED = "received";

    /** One field's share of one entry. */
    public static class Line {
        public long entryId;
        public String kind;
        /** Spending category; null for income. */
        public String category;
        public long amountPaise;
        /** For income: paid already, or still owed (sugar factories often pay months later). */
        public boolean received = true;
        public int fieldId;
        /** The crop season the money belonged to, or null if nothing was planted. */
        public Long seasonId;
    }

    public static class Totals {
        public long spent;
        public long received;
        public long pending;
        public final Map<String, Long> spentByCategory = new LinkedHashMap<>();

        public long profitReceived() {
            return received - spent;
        }

        public long profitIncludingPending() {
            return received + pending - spent;
        }
    }

    private MoneyLedger() {
    }

    /** One field's totals for one season; a null season means entries made while nothing was planted. */
    public static Totals totals(List<Line> lines, int fieldId, Long seasonId) {
        Totals t = new Totals();
        for (Line line : lines) {
            boolean sameSeason = seasonId == null ? line.seasonId == null : seasonId.equals(line.seasonId);
            if (line.fieldId == fieldId && sameSeason) {
                add(t, line);
            }
        }
        return t;
    }

    public static Totals totalsAll(List<Line> lines) {
        Totals t = new Totals();
        for (Line line : lines) {
            add(t, line);
        }
        return t;
    }

    private static void add(Totals t, Line line) {
        if (SPENT.equals(line.kind)) {
            t.spent += line.amountPaise;
            String category = line.category == null ? "other" : line.category;
            Long so_far = t.spentByCategory.get(category);
            t.spentByCategory.put(category, (so_far == null ? 0 : so_far) + line.amountPaise);
        } else if (line.received) {
            t.received += line.amountPaise;
        } else {
            t.pending += line.amountPaise;
        }
    }
}
