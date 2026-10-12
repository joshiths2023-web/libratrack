package com.libratrack.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libratrack.model.Book;
import com.libratrack.model.BookCategory;
import com.libratrack.model.Fine;
import com.libratrack.model.Member;
import com.libratrack.model.MembershipTier;
import com.libratrack.model.Transaction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FineCalculationEngineTest {
    private static final LocalDate DUE = LocalDate.of(2026, 10, 12); // Monday

    private FineCalculationEngine engine;
    private Book textbook;
    private Transaction tx;

    @BeforeEach
    void setUp() {
        engine = new FineCalculationEngine(new HolidayCalendar());
        textbook = new Book(1, "Clean Code", "Martin", BookCategory.TEXTBOOK, 1, 500);
        tx = new Transaction(1, 1, 1, LocalDateTime.of(2026, 10, 5, 10, 0), DUE);
    }

    @Test
    void returnOnDueDateIsFree() {
        Member m = new Member(1, "A", MembershipTier.BASIC);
        Fine fine = engine.calculate(tx, textbook, m, DUE, 1);
        assertEquals(0, fine.getOverdueDays());
        assertEquals(0.0, fine.getNetAmount());
    }

    @Test
    void basicMemberPaysCategoryRateWithFirstOffenseWaiver() {
        Member m = new Member(1, "A", MembershipTier.BASIC);
        Fine fine = engine.calculate(tx, textbook, m, LocalDate.of(2026, 10, 14), 1);
        assertEquals(2, fine.getOverdueDays());
        assertEquals(16.0, fine.getGrossAmount());
        assertEquals(50.0, fine.getWaiverPercent());
        assertEquals(8.0, fine.getNetAmount());
    }

    @Test
    void weekendDaysAreNotCharged() {
        Member m = new Member(1, "A", MembershipTier.BASIC);
        LocalDate friday = LocalDate.of(2026, 10, 9);
        Transaction fridayTx = new Transaction(2, 1, 1, LocalDateTime.of(2026, 10, 2, 10, 0), friday);
        Fine fine = engine.calculate(fridayTx, textbook, m, LocalDate.of(2026, 10, 12), 1);
        assertEquals(1, fine.getOverdueDays());
    }

    @Test
    void graceWeekendHolidayAndWaiverCapCombine() {
        Member vip = new Member(1, "V", MembershipTier.VIP);
        Fine fine = engine.calculate(tx, textbook, vip, LocalDate.of(2026, 10, 21), 1);
        assertEquals(2, fine.getOverdueDays());
        assertEquals(16.0, fine.getGrossAmount());
        assertEquals(FineCalculationEngine.MAX_TOTAL_WAIVER_PERCENT, fine.getWaiverPercent());
        assertEquals(4.0, fine.getNetAmount());
        assertTrue(fine.getWaiverDetails().contains("capped"));
    }

    @Test
    void fineIsCappedAtBookPrice() {
        Book cheap = new Book(2, "Cheap", "X", BookCategory.TEXTBOOK, 1, 10);
        Member m = new Member(1, "A", MembershipTier.BASIC);
        Fine fine = engine.calculate(tx, cheap, m, LocalDate.of(2026, 10, 14), 1);
        assertEquals(10.0, fine.getGrossAmount());
        assertEquals(5.0, fine.getNetAmount());
    }

    @Test
    void bulkReturnWaiverAppliesWhenNotFirstOffense() {
        Member repeat = new Member(1, "R", MembershipTier.BASIC);
        repeat.recordLateReturn();
        Fine fine = engine.calculate(tx, textbook, repeat, LocalDate.of(2026, 10, 14), 3);
        assertEquals(20.0, fine.getWaiverPercent());
        assertEquals(12.8, fine.getNetAmount());
    }

    @Test
    void escalationMultipliersByBand() {
        assertEquals(1.0, engine.escalationMultiplier(1));
        assertEquals(1.0, engine.escalationMultiplier(7));
        assertEquals(1.5, engine.escalationMultiplier(8));
        assertEquals(1.5, engine.escalationMultiplier(14));
        assertEquals(2.0, engine.escalationMultiplier(15));
    }
}
