package com.mvx.agriculture.data;

import java.util.ArrayList;
import java.util.List;

/** Something spent on, or received from, one or more fields. */
public class MoneyEntry {
    public long id;
    /** {@link MoneyLedger#SPENT} or {@link MoneyLedger#RECEIVED}. */
    public String kind;
    public long amountPaise;
    public long day;
    public String category;
    public String crop;
    public double quantity;
    public String unit;
    public long ratePaise;
    public String buyer;
    public boolean received = true;
    public String note;
    public String splitMode;
    public final List<Allocation> allocations = new ArrayList<>();

    public boolean isSpent() {
        return MoneyLedger.SPENT.equals(kind);
    }

    /** One field's share, tied to the crop season standing on it when the money moved. */
    public static class Allocation {
        public int fieldId;
        public Long seasonId;
        public long amountPaise;

        public Allocation(int fieldId, Long seasonId, long amountPaise) {
            this.fieldId = fieldId;
            this.seasonId = seasonId;
            this.amountPaise = amountPaise;
        }
    }
}
