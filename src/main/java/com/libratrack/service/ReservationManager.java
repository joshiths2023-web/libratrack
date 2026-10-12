package com.libratrack.service;

import com.libratrack.dao.BookDao;
import com.libratrack.dao.MemberDao;
import com.libratrack.dao.ReservationDao;
import com.libratrack.model.Book;
import com.libratrack.model.Member;
import com.libratrack.model.Reservation;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Manages holds on books. The queue for a book is ordered by membership tier priority
 * (highest first) and then by request time (earliest first).
 *
 * <p>It also counts how many reservations needed multi-condition queue resolution; these
 * counters feed the custom RFCI metric.
 */
public class ReservationManager {
    private final ReservationDao reservations;
    private final MemberDao members;
    private final BookDao books;

    private int totalReservations;
    private int multiConditionReservations;

    public ReservationManager(ReservationDao reservations, MemberDao members, BookDao books) {
        this.reservations = reservations;
        this.members = members;
        this.books = books;
    }

    public Reservation reserve(long memberId, long bookId, LocalDateTime now) {
        Member member = members.findById(memberId).orElseThrow(() -> new LibraryException("Member not found"));
        Book book = books.findById(bookId).orElseThrow(() -> new LibraryException("Book not found"));
        if (!member.isActive()) {
            throw new LibraryException("Membership is not active");
        }
        boolean duplicate = reservations.findActiveByBook(bookId).stream().anyMatch(r -> r.getMemberId() == memberId);
        if (duplicate) {
            throw new LibraryException("Member already has an active reservation for this book");
        }

        Reservation reservation = new Reservation(reservations.nextId(), memberId, bookId, now);
        reservations.save(reservation);

        totalReservations++;
        if (requiresMultiConditionResolution(book)) {
            multiConditionReservations++;
        }
        return reservation;
    }

    public void cancel(long reservationId) {
        Reservation r = reservations.findById(reservationId)
                .orElseThrow(() -> new LibraryException("Reservation not found"));
        if (!r.isActive()) {
            throw new LibraryException("Reservation is not active");
        }
        r.setStatus(Reservation.Status.CANCELLED);
        reservations.save(r);
    }

    /** Active reservations for a book in fulfilment order. */
    public List<Reservation> queueFor(long bookId) {
        return reservations.findActiveByBook(bookId).stream()
                .sorted(priorityOrder())
                .toList();
    }

    /**
     * A member may take an available copy if they are among the first N reservations in the queue
     * (N = available copies), or if they are not queued and there are more copies than holds.
     */
    public boolean mayIssue(long memberId, Book book) {
        List<Reservation> queue = queueFor(book.getId());
        if (queue.isEmpty()) {
            return true;
        }
        int copies = book.getAvailableCopies();
        for (int position = 0; position < queue.size(); position++) {
            if (queue.get(position).getMemberId() == memberId) {
                return position < copies;
            }
        }
        return queue.size() < copies;
    }

    public void markFulfilled(long memberId, long bookId) {
        reservations.findActiveByBook(bookId).stream()
                .filter(r -> r.getMemberId() == memberId)
                .forEach(r -> {
                    r.setStatus(Reservation.Status.FULFILLED);
                    reservations.save(r);
                });
    }

    /** Reservation Fulfillment Complexity Index: multi-condition reservations / all reservations. */
    public double rfci() {
        return totalReservations == 0 ? 0.0 : (double) multiConditionReservations / totalReservations;
    }

    public int getTotalReservations() {
        return totalReservations;
    }

    public int getMultiConditionReservations() {
        return multiConditionReservations;
    }

    private boolean requiresMultiConditionResolution(Book book) {
        List<Reservation> queue = reservations.findActiveByBook(book.getId());
        if (queue.size() < 2) {
            return false;
        }
        boolean mixedTiers = queue.stream()
                .map(r -> tierPriority(r.getMemberId()))
                .distinct()
                .count() > 1;
        boolean contended = book.getAvailableCopies() < queue.size();
        return mixedTiers || contended;
    }

    private Comparator<Reservation> priorityOrder() {
        return Comparator
                .comparingInt((Reservation r) -> tierPriority(r.getMemberId())).reversed()
                .thenComparing(Reservation::getRequestedAt);
    }

    private int tierPriority(long memberId) {
        return members.findById(memberId).map(m -> m.getTier().getPriority()).orElse(0);
    }
}
