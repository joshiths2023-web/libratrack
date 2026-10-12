package com.libratrack.model;

import java.time.LocalDateTime;

/** A hold placed by a member on a book. */
public class Reservation {

    /** Lifecycle of a reservation. */
    public enum Status { ACTIVE, FULFILLED, CANCELLED }

    private final long id;
    private final long memberId;
    private final long bookId;
    private final LocalDateTime requestedAt;
    private Status status = Status.ACTIVE;

    public Reservation(long id, long memberId, long bookId, LocalDateTime requestedAt) {
        this.id = id;
        this.memberId = memberId;
        this.bookId = bookId;
        this.requestedAt = requestedAt;
    }

    public long getId() {
        return id;
    }

    public long getMemberId() {
        return memberId;
    }

    public long getBookId() {
        return bookId;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
