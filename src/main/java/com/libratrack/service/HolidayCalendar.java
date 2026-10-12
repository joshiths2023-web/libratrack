package com.libratrack.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Knows which days are weekends or library holidays. */
public class HolidayCalendar {
    private final Set<LocalDate> holidays = new HashSet<>();

    public HolidayCalendar() {
        holidays.add(LocalDate.of(2026, 10, 2));
        holidays.add(LocalDate.of(2026, 10, 20));
        holidays.add(LocalDate.of(2026, 11, 8));
        holidays.add(LocalDate.of(2026, 12, 25));
    }

    public void addHoliday(LocalDate date) {
        holidays.add(date);
    }

    public boolean isHoliday(LocalDate date) {
        return holidays.contains(date);
    }

    public boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    public boolean isNonWorkingDay(LocalDate date) {
        return isWeekend(date) || isHoliday(date);
    }
}
