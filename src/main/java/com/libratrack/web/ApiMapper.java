package com.libratrack.web;

import com.libratrack.model.Book;
import com.libratrack.model.Fine;
import com.libratrack.model.Member;
import com.libratrack.model.Reservation;
import com.libratrack.model.Transaction;
import com.libratrack.service.LibraryService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts domain objects into JSON-friendly maps for the web UI. */
public class ApiMapper {
    private final LibraryService library;

    public ApiMapper(LibraryService library) {
        this.library = library;
    }

    public Map<String, Object> state() {
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("now", library.clock().now());
        state.put("books", library.searchBooks("").stream().map(this::book).toList());
        state.put("members", library.members().stream().map(this::member).toList());
        state.put("loans", library.activeLoans().stream().map(this::loan).toList());
        state.put("reservations", library.activeReservations().stream().map(this::reservation).toList());
        state.put("fines", library.fines().stream().map(this::fine).toList());
        return state;
    }

    private Map<String, Object> book(Book b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", b.getId());
        m.put("title", b.getTitle());
        m.put("author", b.getAuthor());
        m.put("category", b.getCategory());
        m.put("available", b.getAvailableCopies());
        m.put("total", b.getTotalCopies());
        m.put("finePerDay", b.getCategory().getFinePerDay());
        m.put("loanDays", b.getCategory().getLoanDays());
        m.put("window", b.getCategory().getWindowStart() + "-" + b.getCategory().getWindowEnd());
        m.put("queue", library.queueFor(b.getId()).size());
        return m;
    }

    private Map<String, Object> member(Member mem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", mem.getId());
        m.put("name", mem.getName());
        m.put("tier", mem.getTier());
        m.put("loans", library.loanCount(mem.getId()));
        m.put("limit", mem.getTier().getMaxBooks());
        m.put("graceDays", mem.getTier().getGraceDays());
        m.put("unpaid", library.unpaidFines(mem.getId()));
        m.put("lateReturns", mem.getLateReturnCount());
        return m;
    }

    private Map<String, Object> loan(Transaction t) {
        LocalDate today = library.clock().now().toLocalDate();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("memberId", t.getMemberId());
        m.put("member", library.member(t.getMemberId()).getName());
        m.put("bookId", t.getBookId());
        m.put("book", library.book(t.getBookId()).getTitle());
        m.put("issuedAt", t.getIssuedAt());
        m.put("due", t.getDueDate());
        boolean overdue = t.getDueDate().isBefore(today);
        m.put("overdue", overdue);
        Fine preview = library.previewFine(t);
        m.put("projectedFine", preview.getNetAmount());
        m.put("projectedDays", preview.getOverdueDays());
        return m;
    }

    private Map<String, Object> reservation(Reservation r) {
        List<Reservation> queue = library.queueFor(r.getBookId());
        int position = 0;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId() == r.getId()) {
                position = i + 1;
            }
        }
        Member member = library.member(r.getMemberId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("member", member.getName());
        m.put("tier", member.getTier());
        m.put("book", library.book(r.getBookId()).getTitle());
        m.put("requestedAt", r.getRequestedAt());
        m.put("position", position);
        return m;
    }

    private Map<String, Object> fine(Fine f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("member", library.member(f.getMemberId()).getName());
        m.put("days", f.getOverdueDays());
        m.put("gross", f.getGrossAmount());
        m.put("waiver", f.getWaiverPercent());
        m.put("waiverDetails", f.getWaiverDetails());
        m.put("net", f.getNetAmount());
        m.put("breakdown", f.getBreakdown());
        m.put("paid", f.isPaid());
        return m;
    }

    /** Reservation counters used for the RFCI custom metric (not shown in the UI). */
    public Map<String, Object> rfci() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalReservations", library.reservations().getTotalReservations());
        m.put("multiCondition", library.reservations().getMultiConditionReservations());
        m.put("rfci", Math.round(library.reservations().rfci() * 1000.0) / 1000.0);
        return m;
    }
}
