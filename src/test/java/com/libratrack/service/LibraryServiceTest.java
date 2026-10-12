package com.libratrack.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libratrack.model.Fine;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Seed ids: members 1 Anubhav(VIP) 2 Joshith(PREMIUM) 3 Priya(BASIC) 4 Rahul(BASIC) 5 Meena(PREMIUM). */
class LibraryServiceTest {
    private static final LocalDateTime MONDAY_10AM = LocalDateTime.of(2026, 10, 12, 10, 0);

    private LibraryService library;

    @BeforeEach
    void setUp() {
        library = new LibraryService(new SimClock(MONDAY_10AM));
        library.seed();
    }

    @Test
    void issueSucceedsAndReducesAvailability() {
        var tx = library.issueBook(3, 1);
        assertEquals(MONDAY_10AM.toLocalDate().plusDays(21), tx.getDueDate());
        assertEquals(1, library.book(1).getAvailableCopies());
    }

    @Test
    void libraryIsClosedOnSunday() {
        library.clock().set(LocalDateTime.of(2026, 10, 11, 10, 0));
        LibraryException e = assertThrows(LibraryException.class, () -> library.issueBook(3, 1));
        assertTrue(e.getMessage().contains("closed"));
    }

    @Test
    void referenceBooksOnlyIssuedInTheirWindow() {
        assertThrows(LibraryException.class, () -> library.issueBook(3, 10));
        library.clock().set(LocalDateTime.of(2026, 10, 12, 15, 0));
        assertEquals(10, library.issueBook(3, 10).getBookId());
    }

    @Test
    void borrowingLimitDependsOnTier() {
        library.issueBook(3, 1);
        library.issueBook(3, 3);
        library.issueBook(3, 4);
        LibraryException e = assertThrows(LibraryException.class, () -> library.issueBook(3, 6));
        assertTrue(e.getMessage().contains("Borrowing limit"));
    }

    @Test
    void higherTierReservationGetsPriorityRegardlessOfRequestTime() {
        var priyaLoan = library.issueBook(3, 5); // Dune, single copy
        library.reserve(4, 5);                    // Rahul (BASIC) first
        library.clock().set(MONDAY_10AM.plusMinutes(5));
        library.reserve(1, 5);                    // Anubhav (VIP) later

        assertEquals(1, library.queueFor(5).get(0).getMemberId());

        library.returnBooks(List.of(priyaLoan.getId()));
        assertThrows(LibraryException.class, () -> library.issueBook(4, 5));
        assertEquals(5, library.issueBook(1, 5).getBookId());
    }

    @Test
    void rfciCountsMultiConditionReservations() {
        library.issueBook(3, 5);
        library.reserve(4, 5);
        library.reserve(1, 5);
        assertEquals(2, library.reservations().getTotalReservations());
        assertEquals(1, library.reservations().getMultiConditionReservations());
        assertEquals(0.5, library.reservations().rfci());
    }

    @Test
    void overdueReturnCreatesFineThatBlocksNothingUntilLimitAndCanBePaid() {
        var tx = library.issueBook(3, 1); // due 2026-11-02
        library.clock().set(LocalDateTime.of(2026, 11, 4, 10, 0));
        var results = library.returnBooks(List.of(tx.getId()));

        Fine fine = results.get(0).fine();
        assertEquals(2, fine.getOverdueDays());
        assertEquals(8.0, fine.getNetAmount());
        assertEquals(8.0, library.unpaidFines(3));

        library.payFine(fine.getId());
        assertEquals(0.0, library.unpaidFines(3));
        assertFalse(library.fines().get(0).getNetAmount() < 0);
    }

    @Test
    void returningTwiceIsRejected() {
        var tx = library.issueBook(3, 1);
        library.returnBooks(List.of(tx.getId()));
        List<Long> again = List.of(tx.getId());
        assertThrows(LibraryException.class, () -> library.returnBooks(again));
    }
}
