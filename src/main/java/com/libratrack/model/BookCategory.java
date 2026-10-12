package com.libratrack.model;

import java.time.LocalTime;

/**
 * Book categories. Each category has its own loan period, daily fine rate and
 * a daily time window during which books of that category may be issued.
 */
public enum BookCategory {
    FICTION(14, 5.0, LocalTime.of(9, 0), LocalTime.of(20, 0)),
    TEXTBOOK(21, 8.0, LocalTime.of(9, 0), LocalTime.of(18, 0)),
    JOURNAL(7, 10.0, LocalTime.of(10, 0), LocalTime.of(17, 0)),
    REFERENCE(2, 20.0, LocalTime.of(14, 0), LocalTime.of(17, 0));

    private final int loanDays;
    private final double finePerDay;
    private final LocalTime windowStart;
    private final LocalTime windowEnd;

    BookCategory(int loanDays, double finePerDay, LocalTime windowStart, LocalTime windowEnd) {
        this.loanDays = loanDays;
        this.finePerDay = finePerDay;
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
    }

    public int getLoanDays() {
        return loanDays;
    }

    public double getFinePerDay() {
        return finePerDay;
    }

    public LocalTime getWindowStart() {
        return windowStart;
    }

    public LocalTime getWindowEnd() {
        return windowEnd;
    }

    public boolean allowsIssueAt(LocalTime time) {
        return !time.isBefore(windowStart) && time.isBefore(windowEnd);
    }
}
