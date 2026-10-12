package com.libratrack.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.libratrack.model.Fine;
import com.libratrack.model.Reservation;
import com.libratrack.service.LibraryService;
import com.libratrack.service.SimClock;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqlitePersistenceTest {
    private static final LocalDateTime MONDAY_10AM = LocalDateTime.of(2026, 10, 12, 10, 0);

    @TempDir
    Path dir;

    private LibraryService open(Database db, LocalDateTime now) {
        return new LibraryService(new SimClock(now), db);
    }

    @Test
    void stateSurvivesRestartAndSeedDoesNotDuplicate() {
        String path = dir.resolve("lib.db").toString();
        long txId;
        long fineId;
        try (Database db = Database.open(path)) {
            LibraryService lib = open(db, MONDAY_10AM);
            lib.seed();
            txId = lib.issueBook(3, 1).getId();
            lib.issueBook(4, 2);
            lib.reserve(1, 2);                                   // Meena/Anubhav queue behind Rahul's loan
            lib.clock().set(MONDAY_10AM.plusDays(40));           // well overdue
            List<?> results = lib.returnBooks(List.of(txId));
            assertEquals(1, results.size());
            Fine fine = lib.fines().get(0);
            fineId = fine.getId();
            lib.payFine(fineId);
        }

        try (Database db = Database.open(path)) {
            LibraryService lib = open(db, MONDAY_10AM.plusDays(40));
            lib.seed();                                          // must be a no-op
            assertEquals(5, lib.members().size());
            assertEquals(12, lib.searchBooks("").size());
            assertEquals(2, lib.book(1).getAvailableCopies());   // returned
            assertEquals(0, lib.book(2).getAvailableCopies());   // still on loan
            assertEquals(1, lib.activeLoans().size());
            assertEquals(1, lib.member(3).getLateReturnCount());
            assertTrue(lib.fines().stream().anyMatch(f -> f.getId() == fineId && f.isPaid()));
            List<Reservation> active = lib.activeReservations();
            assertEquals(1, active.size());

            // ids resume after the highest stored id
            assertEquals(13, lib.addBook("New", "Author", com.libratrack.model.BookCategory.FICTION, 1, 100).getId());
        }
    }
}
