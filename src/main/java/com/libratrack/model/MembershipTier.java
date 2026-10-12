package com.libratrack.model;

/**
 * Membership tiers. Each tier carries its own borrowing limit, overdue grace period,
 * fine waiver percentage and reservation queue priority.
 */
public enum MembershipTier {
    BASIC(3, 0, 0.0, 1),
    PREMIUM(6, 2, 20.0, 2),
    VIP(10, 4, 40.0, 3);

    private final int maxBooks;
    private final int graceDays;
    private final double waiverPercent;
    private final int priority;

    MembershipTier(int maxBooks, int graceDays, double waiverPercent, int priority) {
        this.maxBooks = maxBooks;
        this.graceDays = graceDays;
        this.waiverPercent = waiverPercent;
        this.priority = priority;
    }

    public int getMaxBooks() {
        return maxBooks;
    }

    public int getGraceDays() {
        return graceDays;
    }

    public double getWaiverPercent() {
        return waiverPercent;
    }

    public int getPriority() {
        return priority;
    }
}
