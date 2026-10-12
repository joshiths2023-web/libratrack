package com.libratrack.service;

import com.libratrack.dao.BookDao;
import com.libratrack.dao.Database;
import com.libratrack.dao.FineDao;
import com.libratrack.dao.MemberDao;
import com.libratrack.dao.ReservationDao;
import com.libratrack.dao.TransactionDao;
import com.libratrack.model.Book;
import com.libratrack.model.BookCategory;
import com.libratrack.model.Fine;
import com.libratrack.model.Member;
import com.libratrack.model.MembershipTier;
import com.libratrack.model.Reservation;
import com.libratrack.model.Transaction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Facade used by both the console UI and the web UI. Wires DAOs and services together. */
public class LibraryService {
    private final BookDao bookDao;
    private final MemberDao memberDao;
    private final TransactionDao transactionDao;
    private final ReservationDao reservationDao;
    private final FineDao fineDao;

    private final SimClock clock;
    private final HolidayCalendar calendar = new HolidayCalendar();
    private final FineCalculationEngine fineEngine = new FineCalculationEngine(calendar);
    private final ReservationManager reservationManager;
    private final MembershipService membershipService;
    private final IssueValidator issueValidator;

    /** In-memory library (data is lost on exit). */
    public LibraryService(SimClock clock) {
        this(clock, null);
    }

    /** Library persisted in the given SQLite database (null for in-memory). */
    public LibraryService(SimClock clock, Database db) {
        this.clock = clock;
        // members and books first: transactions, reservations and fines reference them
        if (db == null) {
            bookDao = new BookDao();
            memberDao = new MemberDao();
            transactionDao = new TransactionDao();
            reservationDao = new ReservationDao();
            fineDao = new FineDao();
        } else {
            bookDao = new BookDao(db);
            memberDao = new MemberDao(db);
            transactionDao = new TransactionDao(db);
            reservationDao = new ReservationDao(db);
            fineDao = new FineDao(db);
        }
        reservationManager = new ReservationManager(reservationDao, memberDao, bookDao);
        membershipService = new MembershipService(memberDao, transactionDao);
        issueValidator = new IssueValidator(memberDao, bookDao, transactionDao, fineDao,
                reservationManager, membershipService, calendar);
    }

    // ---- catalogue and members ----

    public List<Book> searchBooks(String query) {
        return bookDao.search(query);
    }

    public Book book(long id) {
        return bookDao.findById(id).orElseThrow(() -> new LibraryException("Book not found"));
    }

    public List<Member> members() {
        return membershipService.all();
    }

    public Member member(long id) {
        return membershipService.find(id);
    }

    public Member registerMember(String name, MembershipTier tier) {
        return membershipService.register(name, tier);
    }

    public Book addBook(String title, String author, BookCategory category, int copies, double price) {
        Book book = new Book(bookDao.nextId(), title, author, category, copies, price);
        bookDao.save(book);
        return book;
    }

    // ---- issue / return ----

    public Transaction issueBook(long memberId, long bookId) {
        LocalDateTime now = clock.now();
        ValidationResult result = issueValidator.validate(memberId, bookId, now);
        if (!result.isValid()) {
            throw new LibraryException(result.summary());
        }
        Book book = book(bookId);
        book.checkOut();
        bookDao.save(book);
        reservationManager.markFulfilled(memberId, bookId);
        Transaction tx = new Transaction(transactionDao.nextId(), memberId, bookId, now,
                now.toLocalDate().plusDays(book.getCategory().getLoanDays()));
        transactionDao.save(tx);
        return tx;
    }

    public List<ReturnResult> returnBooks(List<Long> transactionIds) {
        if (transactionIds.isEmpty()) {
            throw new LibraryException("Select at least one loan to return");
        }
        LocalDateTime now = clock.now();
        List<ReturnResult> results = new ArrayList<>();
        for (long txId : transactionIds) {
            Transaction tx = transactionDao.findById(txId)
                    .orElseThrow(() -> new LibraryException("Transaction " + txId + " not found"));
            if (tx.isReturned()) {
                throw new LibraryException("Transaction " + txId + " was already returned");
            }
            Book book = book(tx.getBookId());
            Member member = member(tx.getMemberId());
            Fine fine = fineEngine.calculate(tx, book, member, now.toLocalDate(), transactionIds.size());
            tx.markReturned(now);
            transactionDao.save(tx);
            book.checkIn();
            bookDao.save(book);
            if (fine.getOverdueDays() > 0) {
                member.recordLateReturn();
                memberDao.save(member);
                fineDao.add(fine);
                results.add(new ReturnResult(tx, fine));
            } else {
                results.add(new ReturnResult(tx, null));
            }
        }
        return results;
    }

    public Fine previewFine(Transaction tx) {
        return fineEngine.calculate(tx, book(tx.getBookId()), member(tx.getMemberId()),
                clock.now().toLocalDate(), 1);
    }

    public List<Transaction> activeLoans() {
        return transactionDao.findActive();
    }

    public List<Transaction> allTransactions() {
        return transactionDao.findAll();
    }

    // ---- reservations ----

    public Reservation reserve(long memberId, long bookId) {
        return reservationManager.reserve(memberId, bookId, clock.now());
    }

    public void cancelReservation(long reservationId) {
        reservationManager.cancel(reservationId);
    }

    public List<Reservation> activeReservations() {
        return reservationDao.findActive();
    }

    public List<Reservation> queueFor(long bookId) {
        return reservationManager.queueFor(bookId);
    }

    // ---- fines ----

    public List<Fine> fines() {
        return fineDao.findAll();
    }

    public Fine payFine(long fineId) {
        Fine fine = fineDao.findById(fineId).orElseThrow(() -> new LibraryException("Fine not found"));
        if (fine.isPaid()) {
            throw new LibraryException("Fine is already paid");
        }
        fine.markPaid();
        fineDao.save(fine);
        return fine;
    }

    public double unpaidFines(long memberId) {
        return fineDao.unpaidTotal(memberId);
    }

    // ---- misc ----

    public SimClock clock() {
        return clock;
    }

    public ReservationManager reservations() {
        return reservationManager;
    }

    public int loanCount(long memberId) {
        return membershipService.activeLoanCount(memberId);
    }

    /** Loads a small demonstration data set. */
    public void seed() {
        if (!memberDao.findAll().isEmpty() || !bookDao.findAll().isEmpty()) {
            return; // already populated (e.g. loaded from the database)
        }
        registerMember("Anubhav B", MembershipTier.VIP);
        registerMember("Joshith S", MembershipTier.PREMIUM);
        registerMember("Priya R", MembershipTier.BASIC);
        registerMember("Rahul K", MembershipTier.BASIC);
        registerMember("Meena T", MembershipTier.PREMIUM);

        addBook("Clean Code", "Robert C. Martin", BookCategory.TEXTBOOK, 2, 450);
        addBook("Software Metrics: A Rigorous Approach", "Norman Fenton", BookCategory.TEXTBOOK, 1, 600);
        addBook("Refactoring", "Martin Fowler", BookCategory.TEXTBOOK, 2, 500);
        addBook("The Hobbit", "J.R.R. Tolkien", BookCategory.FICTION, 3, 300);
        addBook("Dune", "Frank Herbert", BookCategory.FICTION, 1, 350);
        addBook("Wings of Fire", "A.P.J. Abdul Kalam", BookCategory.FICTION, 2, 250);
        addBook("Malgudi Days", "R.K. Narayan", BookCategory.FICTION, 2, 200);
        addBook("IEEE Software Vol. 42", "IEEE", BookCategory.JOURNAL, 2, 150);
        addBook("ACM Computing Surveys", "ACM", BookCategory.JOURNAL, 1, 180);
        addBook("Encyclopaedia of Computing", "Various", BookCategory.REFERENCE, 1, 800);
        addBook("Oxford English Dictionary", "Oxford", BookCategory.REFERENCE, 1, 700);
        addBook("Design Patterns", "Gamma et al.", BookCategory.TEXTBOOK, 1, 550);
    }
}
