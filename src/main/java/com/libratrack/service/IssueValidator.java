package com.libratrack.service;

import com.libratrack.dao.BookDao;
import com.libratrack.dao.FineDao;
import com.libratrack.dao.MemberDao;
import com.libratrack.dao.TransactionDao;
import com.libratrack.model.Book;
import com.libratrack.model.Member;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Applies every rule that must hold before a book can be issued. */
public class IssueValidator {
    public static final LocalTime OPENING_TIME = LocalTime.of(9, 0);
    public static final LocalTime CLOSING_TIME = LocalTime.of(20, 0);
    public static final double MAX_UNPAID_FINES = 200.0;

    private final MemberDao members;
    private final BookDao books;
    private final TransactionDao transactions;
    private final FineDao fines;
    private final ReservationManager reservations;
    private final MembershipService membership;
    private final HolidayCalendar calendar;

    public IssueValidator(MemberDao members, BookDao books, TransactionDao transactions, FineDao fines,
                          ReservationManager reservations, MembershipService membership, HolidayCalendar calendar) {
        this.members = members;
        this.books = books;
        this.transactions = transactions;
        this.fines = fines;
        this.reservations = reservations;
        this.membership = membership;
        this.calendar = calendar;
    }

    public ValidationResult validate(long memberId, long bookId, LocalDateTime now) {
        ValidationResult result = new ValidationResult();
        Member member = members.findById(memberId).orElse(null);
        Book book = books.findById(bookId).orElse(null);
        if (member == null) {
            result.add("Member not found");
        }
        if (book == null) {
            result.add("Book not found");
        }
        if (member == null || book == null) {
            return result;
        }

        checkMembership(member, result);
        checkOpeningHours(now, result);
        checkCategoryWindow(book, now, result);
        checkAvailability(book, result);
        checkBorrowingLimit(member, result);
        checkDuplicateLoan(member, book, result);
        checkOutstandingFines(member, result);
        checkReservationPriority(member, book, result);
        return result;
    }

    private void checkMembership(Member member, ValidationResult result) {
        if (!member.isActive()) {
            result.add("Membership is not active");
        }
    }

    private void checkOpeningHours(LocalDateTime now, ValidationResult result) {
        if (now.getDayOfWeek() == DayOfWeek.SUNDAY || calendar.isHoliday(now.toLocalDate())) {
            result.add("Library is closed today");
            return;
        }
        LocalTime time = now.toLocalTime();
        if (time.isBefore(OPENING_TIME) || !time.isBefore(CLOSING_TIME)) {
            result.add("Library is closed (open " + OPENING_TIME + "-" + CLOSING_TIME + ")");
        }
    }

    private void checkCategoryWindow(Book book, LocalDateTime now, ValidationResult result) {
        if (!book.getCategory().allowsIssueAt(now.toLocalTime())) {
            result.add(book.getCategory() + " books can only be issued between "
                    + book.getCategory().getWindowStart() + " and " + book.getCategory().getWindowEnd());
        }
    }

    private void checkAvailability(Book book, ValidationResult result) {
        if (!book.isAvailable()) {
            result.add("No copies available - you can reserve this book");
        }
    }

    private void checkBorrowingLimit(Member member, ValidationResult result) {
        if (!membership.canBorrowMore(member)) {
            result.add("Borrowing limit reached for " + member.getTier() + " tier ("
                    + member.getTier().getMaxBooks() + " books)");
        }
    }

    private void checkDuplicateLoan(Member member, Book book, ValidationResult result) {
        if (transactions.findActiveByMemberAndBook(member.getId(), book.getId()).isPresent()) {
            result.add("Member already has this book on loan");
        }
    }

    private void checkOutstandingFines(Member member, ValidationResult result) {
        double unpaid = fines.unpaidTotal(member.getId());
        if (unpaid > MAX_UNPAID_FINES) {
            result.add("Unpaid fines of " + unpaid + " exceed the limit of " + MAX_UNPAID_FINES);
        }
    }

    private void checkReservationPriority(Member member, Book book, ValidationResult result) {
        if (book.isAvailable() && !reservations.mayIssue(member.getId(), book)) {
            result.add("Remaining copies are held for members with higher reservation priority");
        }
    }
}
