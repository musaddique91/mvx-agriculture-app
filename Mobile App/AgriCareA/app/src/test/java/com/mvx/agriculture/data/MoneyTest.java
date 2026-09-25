package com.mvx.agriculture.data;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class MoneyTest {

    private static long sum(long[] values) {
        long total = 0;
        for (long v : values) {
            total += v;
        }
        return total;
    }

    @Test
    public void areaSplitFollowsAcres() {
        // Rs 6,000 of urea over a 4.8 acre and a 1.2 acre plot
        assertArrayEquals(new long[]{480_000, 120_000}, MoneySplit.byArea(600_000, new double[]{4.8, 1.2}));
    }

    @Test
    public void equalSplitNeverLosesAPaisa() {
        long[] shares = MoneySplit.equally(10_000, 3);   // Rs 100 over 3 fields
        assertEquals(10_000, sum(shares));
        assertArrayEquals(new long[]{3_334, 3_333, 3_333}, shares);
    }

    @Test
    public void awkwardAreaSplitsStillAddUpExactly() {
        long[] shares = MoneySplit.byArea(100_001, new double[]{1.3, 2.7, 0.9, 4.1});
        assertEquals(100_001, sum(shares));
    }

    @Test
    public void fieldsWithNoAreaAreSplitEqually() {
        assertArrayEquals(new long[]{5_000, 5_000}, MoneySplit.byArea(10_000, new double[]{0, 0}));
    }

    @Test
    public void oneFieldTakesTheWholeAmount() {
        assertArrayEquals(new long[]{123_456}, MoneySplit.byArea(123_456, new double[]{2.5}));
        assertArrayEquals(new long[]{123_456}, MoneySplit.equally(123_456, 1));
    }

    @Test
    public void rupeesAndPaiseConvertBothWays() {
        assertEquals(125_050, Money.toPaise("1250.50"));
        assertEquals(125_000, Money.toPaise("1,250"));
        assertEquals(-1, Money.toPaise("abc"));
        assertEquals(-1, Money.toPaise(""));
        assertEquals("₹1,25,000", Money.format(12_500_000));
        assertEquals("₹1,250.50", Money.format(125_050));
    }

    private static MoneyLedger.Line line(String kind, String category, long paise, boolean received,
                                         int fieldId, Long seasonId) {
        MoneyLedger.Line l = new MoneyLedger.Line();
        l.kind = kind;
        l.category = category;
        l.amountPaise = paise;
        l.received = received;
        l.fieldId = fieldId;
        l.seasonId = seasonId;
        return l;
    }

    @Test
    public void seasonTotalsCountOnlyThatFieldsSeason() {
        List<MoneyLedger.Line> lines = Arrays.asList(
                line(MoneyLedger.SPENT, "fertilizer", 480_000, true, 1, 7L),
                line(MoneyLedger.SPENT, "labour", 150_000, true, 1, 7L),
                line(MoneyLedger.SPENT, "fertilizer", 120_000, true, 2, 9L),      // another field
                line(MoneyLedger.SPENT, "seed", 90_000, true, 1, 3L),             // an older season
                line(MoneyLedger.RECEIVED, null, 2_000_000, true, 1, 7L),
                line(MoneyLedger.RECEIVED, null, 800_000, false, 1, 7L));        // factory has not paid yet

        MoneyLedger.Totals t = MoneyLedger.totals(lines, 1, 7L);

        assertEquals(630_000, t.spent);
        assertEquals(2_000_000, t.received);
        assertEquals(800_000, t.pending);
        assertEquals(1_370_000, t.profitReceived());          // what is in hand
        assertEquals(2_170_000, t.profitIncludingPending());  // once the factory pays
        assertEquals(Long.valueOf(480_000), t.spentByCategory.get("fertilizer"));
        assertEquals(Long.valueOf(150_000), t.spentByCategory.get("labour"));
    }

    @Test
    public void aNullSeasonMeansEntriesMadeWhileNothingWasPlanted() {
        List<MoneyLedger.Line> lines = Arrays.asList(
                line(MoneyLedger.SPENT, "machinery", 200_000, true, 1, null),
                line(MoneyLedger.SPENT, "seed", 90_000, true, 1, 3L));
        assertEquals(200_000, MoneyLedger.totals(lines, 1, null).spent);
    }

    @Test
    public void allFieldsTotalsAddEveryField() {
        List<MoneyLedger.Line> lines = Arrays.asList(
                line(MoneyLedger.SPENT, "seed", 100, true, 1, 7L),
                line(MoneyLedger.SPENT, "seed", 200, true, 2, 9L),
                line(MoneyLedger.RECEIVED, null, 1_000, true, 2, 9L));
        MoneyLedger.Totals t = MoneyLedger.totalsAll(lines);
        assertEquals(300, t.spent);
        assertEquals(1_000, t.received);
    }
}
