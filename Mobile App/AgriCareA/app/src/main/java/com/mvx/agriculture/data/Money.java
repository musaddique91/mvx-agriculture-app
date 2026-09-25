package com.mvx.agriculture.data;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Rupee amounts kept as whole paise, so sums never drift by a fraction. */
public final class Money {

    private Money() {
    }

    /** "1,250.50" to 125050 paise; -1 when the text is not an amount. */
    public static long toPaise(String text) {
        if (text == null) {
            return -1;
        }
        String clean = text.replace(",", "").replace("₹", "").trim();
        if (clean.isEmpty()) {
            return -1;
        }
        try {
            return new BigDecimal(clean).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return -1;
        }
    }

    /** Indian grouping (₹1,25,000), with paise only when there are some. */
    public static String format(long paise) {
        boolean negative = paise < 0;
        long abs = Math.abs(paise);
        String rupees = groupIndian(abs / 100);
        long rest = abs % 100;
        String text = "₹" + rupees + (rest == 0 ? "" : String.format(java.util.Locale.ROOT, ".%02d", rest));
        return negative ? "-" + text : text;
    }

    /** Plain rupees for an input box, e.g. 1250.5 as "1250.50". */
    public static String toInput(long paise) {
        return BigDecimal.valueOf(paise).movePointLeft(2).setScale(paise % 100 == 0 ? 0 : 2, RoundingMode.UNNECESSARY).toPlainString();
    }

    private static String groupIndian(long rupees) {
        String digits = Long.toString(rupees);
        if (digits.length() <= 3) {
            return digits;
        }
        String last3 = digits.substring(digits.length() - 3);
        String head = digits.substring(0, digits.length() - 3);
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < head.length(); i++) {
            if (i > 0 && (head.length() - i) % 2 == 0) {
                grouped.append(',');
            }
            grouped.append(head.charAt(i));
        }
        return grouped + "," + last3;
    }
}
