package com.libratrack.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** An issue/return record for one copy of a book. */
public class Transaction {
    private final long id;
    private final long memberId;
    private final long bookId;
    private final LocalDateTime issuedAt;
    private final LocalDate dueDate;
    private LocalDateTime returnedAt;

    public Transaction(long id, long memberId, long bookId, LocalDateTime issuedAt, LocalDate dueDate) {
        this.id = id;
        this.memberId = memberId;
        this.bookId = bookId;
        this.issuedAt = issuedAt;
        this.dueDate = dueDate;
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

    public LocalDateTime getIssuedAt() {
        return issuedAt;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public boolean isReturned() {
        return returnedAt != null;
    }

    public void markReturned(LocalDateTime when) {
        this.returnedAt = when;
    }
}
