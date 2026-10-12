package com.libratrack.model;

/** The result of a fine calculation for one overdue return. */
public class Fine {
    private long id;
    private final long transactionId;
    private final long memberId;
    private final int overdueDays;
    private final double grossAmount;
    private final double waiverPercent;
    private final String waiverDetails;
    private final double netAmount;
    private final String breakdown;
    private boolean paid;

    public Fine(long transactionId, long memberId, int overdueDays, double grossAmount,
                double waiverPercent, String waiverDetails, double netAmount, String breakdown) {
        this.transactionId = transactionId;
        this.memberId = memberId;
        this.overdueDays = overdueDays;
        this.grossAmount = grossAmount;
        this.waiverPercent = waiverPercent;
        this.waiverDetails = waiverDetails;
        this.netAmount = netAmount;
        this.breakdown = breakdown;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getTransactionId() {
        return transactionId;
    }

    public long getMemberId() {
        return memberId;
    }

    public int getOverdueDays() {
        return overdueDays;
    }

    public double getGrossAmount() {
        return grossAmount;
    }

    public double getWaiverPercent() {
        return waiverPercent;
    }

    public String getWaiverDetails() {
        return waiverDetails;
    }

    public double getNetAmount() {
        return netAmount;
    }

    public String getBreakdown() {
        return breakdown;
    }

    public boolean isPaid() {
        return paid;
    }

    public void markPaid() {
        this.paid = true;
    }
}
