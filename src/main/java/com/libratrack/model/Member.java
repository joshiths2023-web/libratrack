package com.libratrack.model;

/** A library member. */
public class Member {
    private final long id;
    private final String name;
    private MembershipTier tier;
    private boolean active;
    private int lateReturnCount;

    public Member(long id, String name, MembershipTier tier) {
        this.id = id;
        this.name = name;
        this.tier = tier;
        this.active = true;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public MembershipTier getTier() {
        return tier;
    }

    public void setTier(MembershipTier tier) {
        this.tier = tier;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getLateReturnCount() {
        return lateReturnCount;
    }

    /** Restores stored state when loading from the database. */
    public void restoreState(boolean active, int lateReturnCount) {
        this.active = active;
        this.lateReturnCount = lateReturnCount;
    }

    public void recordLateReturn() {
        lateReturnCount++;
    }
}
