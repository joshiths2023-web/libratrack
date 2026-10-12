package com.libratrack.ui;

import com.libratrack.model.Book;
import com.libratrack.model.Fine;
import com.libratrack.model.Member;
import com.libratrack.model.Reservation;
import com.libratrack.model.Transaction;
import com.libratrack.service.LibraryException;
import com.libratrack.service.LibraryService;
import com.libratrack.service.ReturnResult;

import java.io.InputStream;
import java.io.PrintStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** Console-driven interface covering the full search-issue-return-fine flow. */
public class ConsoleUI {
    private final LibraryService library;
    private final Scanner in;
    private final PrintStream out;

    public ConsoleUI(LibraryService library, InputStream input, PrintStream output) {
        this.library = library;
        this.in = new Scanner(input);
        this.out = output;
    }

    public void run() {
        out.println("=== LibraTrack - Library Management System ===");
        boolean running = true;
        while (running) {
            printMenu();
            String choice = prompt("Choose");
            if (choice == null) {
                break;
            }
            try {
                running = dispatch(choice.trim());
            } catch (LibraryException e) {
                out.println("  ! " + e.getMessage());
            } catch (NumberFormatException e) {
                out.println("  ! Please enter a number");
            }
        }
        out.println("Goodbye.");
    }

    private boolean dispatch(String choice) {
        switch (choice) {
            case "1" -> searchBooks();
            case "2" -> listMembers();
            case "3" -> issue();
            case "4" -> returnBooks();
            case "5" -> reserve();
            case "6" -> showLoans();
            case "7" -> fines();
            case "8" -> advanceClock();
            case "0" -> {
                return false;
            }
            default -> out.println("  ! Unknown option");
        }
        return true;
    }

    private void printMenu() {
        out.println();
        out.println("Date/time: " + library.clock().now());
        out.println(" 1) Search catalogue      6) Active loans");
        out.println(" 2) List members          7) Fines (view / pay)");
        out.println(" 3) Issue a book          8) Advance clock");
        out.println(" 4) Return book(s)        0) Exit");
        out.println(" 5) Reserve a book");
    }

    private void searchBooks() {
        String query = prompt("Search (blank = all)");
        for (Book b : library.searchBooks(query)) {
            out.printf(Locale.ROOT, "  [%d] %-40s %-10s %d/%d available%n", b.getId(), b.getTitle(),
                    b.getCategory(), b.getAvailableCopies(), b.getTotalCopies());
        }
    }

    private void listMembers() {
        for (Member m : library.members()) {
            out.printf(Locale.ROOT, "  [%d] %-12s %-8s loans=%d unpaid fines=%.2f%n", m.getId(), m.getName(),
                    m.getTier(), library.loanCount(m.getId()), library.unpaidFines(m.getId()));
        }
    }

    private void issue() {
        long memberId = Long.parseLong(prompt("Member id"));
        long bookId = Long.parseLong(prompt("Book id"));
        Transaction tx = library.issueBook(memberId, bookId);
        out.println("  Issued. Transaction " + tx.getId() + ", due " + tx.getDueDate());
    }

    private void returnBooks() {
        String raw = prompt("Transaction id(s), comma separated");
        List<Long> ids = new ArrayList<>();
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                ids.add(Long.parseLong(part.trim()));
            }
        }
        for (ReturnResult r : library.returnBooks(ids)) {
            describeReturn(r);
        }
    }

    private void describeReturn(ReturnResult r) {
        String title = library.book(r.transaction().getBookId()).getTitle();
        if (r.fine() == null) {
            out.println("  Returned '" + title + "' - no fine.");
            return;
        }
        Fine f = r.fine();
        out.printf(Locale.ROOT, "  Returned '%s' - %d overdue day(s), gross %.2f, waivers: %s, FINE %.2f (id %d)%n",
                title, f.getOverdueDays(), f.getGrossAmount(), f.getWaiverDetails(), f.getNetAmount(), f.getId());
    }

    private void reserve() {
        long memberId = Long.parseLong(prompt("Member id"));
        long bookId = Long.parseLong(prompt("Book id"));
        Reservation r = library.reserve(memberId, bookId);
        out.println("  Reservation " + r.getId() + " placed. Queue position: " + position(r));
    }

    private int position(Reservation r) {
        List<Reservation> queue = library.queueFor(r.getBookId());
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).getId() == r.getId()) {
                return i + 1;
            }
        }
        return -1;
    }

    private void showLoans() {
        LocalDateTime now = library.clock().now();
        for (Transaction t : library.activeLoans()) {
            String flag = t.getDueDate().isBefore(now.toLocalDate()) ? " OVERDUE" : "";
            out.println("  tx " + t.getId() + ": " + library.member(t.getMemberId()).getName() + " has '"
                    + library.book(t.getBookId()).getTitle() + "' due " + t.getDueDate() + flag);
        }
    }

    private void fines() {
        for (Fine f : library.fines()) {
            out.printf(Locale.ROOT, "  fine %d: member %d, %.2f (%s) %s%n", f.getId(), f.getMemberId(),
                    f.getNetAmount(), f.getWaiverDetails(), f.isPaid() ? "PAID" : "UNPAID");
        }
        String id = prompt("Fine id to pay (blank to skip)");
        if (id != null && !id.isBlank()) {
            library.payFine(Long.parseLong(id.trim()));
            out.println("  Paid.");
        }
    }

    private void advanceClock() {
        long days = Long.parseLong(prompt("Days to advance"));
        library.clock().advanceDays(days);
        out.println("  Now " + library.clock().now());
    }

    private String prompt(String label) {
        out.print(label + "> ");
        out.flush();
        return in.hasNextLine() ? in.nextLine() : null;
    }
}
