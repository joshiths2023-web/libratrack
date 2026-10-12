package com.libratrack.dao;

import com.libratrack.model.Book;
import com.libratrack.model.BookCategory;

import java.util.List;
import java.util.Locale;

/** Storage of books (in memory, optionally backed by SQLite). */
public class BookDao extends InMemoryDao<Book> {

    public BookDao() {
        super();
    }

    public BookDao(Database db) {
        super(db, "books");
        db.execute("CREATE TABLE IF NOT EXISTS books (id INTEGER PRIMARY KEY, title TEXT NOT NULL, "
                + "author TEXT NOT NULL, category TEXT NOT NULL, total_copies INTEGER NOT NULL, "
                + "available_copies INTEGER NOT NULL, price REAL NOT NULL)");
        load();
    }

    @Override
    protected long idOf(Book book) {
        return book.getId();
    }

    @Override
    protected void persist(Book b) {
        db.execute("INSERT INTO books (id, title, author, category, total_copies, available_copies, price) "
                + "VALUES (?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET available_copies = excluded.available_copies", b.getId(), b.getTitle(), b.getAuthor(), b.getCategory().name(),
                b.getTotalCopies(), b.getAvailableCopies(), b.getPrice());
    }

    @Override
    protected List<Book> loadAll() {
        return db.query("SELECT * FROM books ORDER BY id", rs -> {
            Book b = new Book(rs.getLong("id"), rs.getString("title"), rs.getString("author"),
                    BookCategory.valueOf(rs.getString("category")), rs.getInt("total_copies"), rs.getDouble("price"));
            b.restoreAvailableCopies(rs.getInt("available_copies"));
            return b;
        });
    }

    public List<Book> search(String query) {
        if (query == null || query.isBlank()) {
            return findAll();
        }
        String q = query.toLowerCase(Locale.ROOT).trim();
        return findAll().stream()
                .filter(b -> b.getTitle().toLowerCase(Locale.ROOT).contains(q)
                        || b.getAuthor().toLowerCase(Locale.ROOT).contains(q)
                        || b.getCategory().name().toLowerCase(Locale.ROOT).contains(q))
                .toList();
    }
}
