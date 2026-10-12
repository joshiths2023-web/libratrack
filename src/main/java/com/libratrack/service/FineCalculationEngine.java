package com.libratrack.service;

import com.libratrack.model.Book;
import com.libratrack.model.Fine;
import com.libratrack.model.Member;
import com.libratrack.model.Transaction;

import java.time.LocalDate;
import java.util.Locale;

/**
 * Dynamic overdue-fine calculation.
 *
 * <p>Rules applied, in order:
 * <ol>
 *   <li>Category-based daily rate.</li>
 *   <li>Membership-tier grace period (free days after the due date).</li>
 *   <li>Weekend / holiday exemption (those days are never chargeable).</li>
 *   <li>Escalating overdue tiers: days 1-7 at 1.0x, days 8-14 at 1.5x, day 15+ at 2.0x.</li>
 *   <li>Cap: the gross fine never exceeds the replacement price of the book.</li>
 *   <li>Waivers: tier waiver, plus at most one of first-offense / bulk-return waiver
 *       (the larger one), with the combined waiver capped at {@value #MAX_TOTAL_WAIVER_PERCENT}%.</li>
 * </ol>
 */
public class FineCalculationEngine {
    public static final double MAX_TOTAL_WAIVER_PERCENT = 75.0;
    public static final double FIRST_OFFENSE_WAIVER_PERCENT = 50.0;
    public static final double BULK_WAIVER_PERCENT = 20.0;
    public static final int BULK_RETURN_THRESHOLD = 3;
    public static final int ESCALATION_TIER_1_END = 7;
    public static final int ESCALATION_TIER_2_END = 14;
    public static final double ESCALATION_TIER_2_MULTIPLIER = 1.5;
    public static final double ESCALATION_TIER_3_MULTIPLIER = 2.0;

    private final HolidayCalendar calendar;

    public FineCalculationEngine(HolidayCalendar calendar) {
        this.calendar = calendar;
    }

    /**
     * Calculates the fine for returning {@code tx} on {@code returnDate}.
     *
     * @param returnedTogether number of books returned in the same visit (for the bulk-return waiver)
     * @return a fine with {@code overdueDays == 0} when nothing is owed
     */
    public Fine calculate(Transaction tx, Book book, Member member, LocalDate returnDate, int returnedTogether) {
        LocalDate chargeableFrom = tx.getDueDate().plusDays(member.getTier().getGraceDays() + 1L);
        double rate = book.getCategory().getFinePerDay();
        int chargeableDays = 0;
        double gross = 0;

        for (LocalDate day = chargeableFrom; !day.isAfter(returnDate); day = day.plusDays(1)) {
            if (calendar.isNonWorkingDay(day)) {
                continue;
            }
            chargeableDays++;
            gross += rate * escalationMultiplier(chargeableDays);
        }

        if (chargeableDays == 0) {
            return new Fine(tx.getId(), member.getId(), 0, 0, 0, "none", 0, "Returned on time or within grace period");
        }

        StringBuilder note = new StringBuilder();
        note.append(String.format(Locale.ROOT, "%d chargeable day(s) at %.2f/day base", chargeableDays, rate));
        if (gross > book.getPrice()) {
            gross = book.getPrice();
            note.append("; capped at book price ").append(round(book.getPrice()));
        }

        StringBuilder waiverLabel = new StringBuilder();
        double waiver = totalWaiver(member, returnedTogether, waiverLabel);
        double net = round(gross * (1 - waiver / 100.0));
        return new Fine(tx.getId(), member.getId(), chargeableDays, round(gross), waiver,
                waiverLabel.toString(), net, note.toString());
    }

    double escalationMultiplier(int overdueDayNumber) {
        if (overdueDayNumber <= ESCALATION_TIER_1_END) {
            return 1.0;
        }
        if (overdueDayNumber <= ESCALATION_TIER_2_END) {
            return ESCALATION_TIER_2_MULTIPLIER;
        }
        return ESCALATION_TIER_3_MULTIPLIER;
    }

    private double totalWaiver(Member member, int returnedTogether, StringBuilder label) {
        double tierWaiver = member.getTier().getWaiverPercent();
        if (tierWaiver > 0) {
            label.append(member.getTier()).append(" tier ").append((int) tierWaiver).append("%");
        }

        boolean firstOffense = member.getLateReturnCount() == 0;
        boolean bulk = returnedTogether >= BULK_RETURN_THRESHOLD;
        double extra = 0;
        String extraLabel = "";
        if (firstOffense) {
            extra = FIRST_OFFENSE_WAIVER_PERCENT;
            extraLabel = "first-offense " + (int) FIRST_OFFENSE_WAIVER_PERCENT + "%";
        }
        if (bulk && BULK_WAIVER_PERCENT > extra) {
            extra = BULK_WAIVER_PERCENT;
            extraLabel = "bulk-return " + (int) BULK_WAIVER_PERCENT + "%";
        }
        if (extra > 0) {
            if (label.length() > 0) {
                label.append(" + ");
            }
            label.append(extraLabel);
        }

        double total = tierWaiver + extra;
        if (total > MAX_TOTAL_WAIVER_PERCENT) {
            total = MAX_TOTAL_WAIVER_PERCENT;
            label.append(" (capped at ").append((int) MAX_TOTAL_WAIVER_PERCENT).append("%)");
        }
        if (label.length() == 0) {
            label.append("none");
        }
        return total;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
