package com.libratrack.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** A controllable clock so overdue scenarios can be demonstrated without waiting. */
public class SimClock {
    private LocalDateTime now;

    public SimClock() {
        this.now = LocalDate.now().atTime(LocalTime.of(10, 0));
    }

    public SimClock(LocalDateTime start) {
        this.now = start;
    }

    public synchronized LocalDateTime now() {
        return now;
    }

    public synchronized void set(LocalDateTime value) {
        this.now = value;
    }

    public synchronized void advanceDays(long days) {
        this.now = now.plusDays(days);
    }

    public synchronized void advanceHours(long hours) {
        this.now = now.plusHours(hours);
    }
}
