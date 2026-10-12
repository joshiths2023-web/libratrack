package com.libratrack.dao;

import com.libratrack.model.Transaction;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Storage of issue/return transactions (in memory, optionally backed by SQLite). */
public class TransactionDao extends InMemoryDao<Transaction> {

    public TransactionDao() {
        super();
    }

    public TransactionDao(Database db) {
        super(db, "transactions");
        db.execute("CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY, member_id INTEGER NOT NULL, "
                + "book_id INTEGER NOT NULL, issued_at TEXT NOT NULL, due_date TEXT NOT NULL, returned_at TEXT, "
                + "FOREIGN KEY (member_id) REFERENCES members(id), FOREIGN KEY (book_id) REFERENCES books(id))");
        load();
    }

    @Override
    protected long idOf(Transaction transaction) {
        return transaction.getId();
    }

    @Override
    protected void persist(Transaction t) {
        db.execute("INSERT OR REPLACE INTO transactions (id, member_id, book_id, issued_at, due_date, returned_at) "
                + "VALUES (?,?,?,?,?,?)", t.getId(), t.getMemberId(), t.getBookId(), t.getIssuedAt().toString(),
                t.getDueDate().toString(), t.getReturnedAt() == null ? null : t.getReturnedAt().toString());
    }

    @Override
    protected List<Transaction> loadAll() {
        return db.query("SELECT * FROM transactions ORDER BY id", rs -> {
            Transaction t = new Transaction(rs.getLong("id"), rs.getLong("member_id"), rs.getLong("book_id"),
                    LocalDateTime.parse(rs.getString("issued_at")), LocalDate.parse(rs.getString("due_date")));
            String returned = rs.getString("returned_at");
            if (returned != null) {
                t.markReturned(LocalDateTime.parse(returned));
            }
            return t;
        });
    }

    public List<Transaction> findActive() {
        return findAll().stream().filter(t -> !t.isReturned()).toList();
    }

    public List<Transaction> findActiveByMember(long memberId) {
        return findActive().stream().filter(t -> t.getMemberId() == memberId).toList();
    }

    public Optional<Transaction> findActiveByMemberAndBook(long memberId, long bookId) {
        return findActiveByMember(memberId).stream().filter(t -> t.getBookId() == bookId).findFirst();
    }

    public List<Transaction> findOverdue(LocalDate today) {
        return findActive().stream().filter(t -> t.getDueDate().isBefore(today)).toList();
    }
}
