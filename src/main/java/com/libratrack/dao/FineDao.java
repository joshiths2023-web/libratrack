package com.libratrack.dao;

import com.libratrack.model.Fine;

import java.util.List;

/** Storage of fines (in memory, optionally backed by SQLite). */
public class FineDao extends InMemoryDao<Fine> {

    public FineDao() {
        super();
    }

    public FineDao(Database db) {
        super(db, "fines");
        db.execute("CREATE TABLE IF NOT EXISTS fines (id INTEGER PRIMARY KEY, transaction_id INTEGER NOT NULL, "
                + "member_id INTEGER NOT NULL, overdue_days INTEGER NOT NULL, gross_amount REAL NOT NULL, "
                + "waiver_percent REAL NOT NULL, waiver_details TEXT, net_amount REAL NOT NULL, breakdown TEXT, "
                + "paid INTEGER NOT NULL, FOREIGN KEY (transaction_id) REFERENCES transactions(id), "
                + "FOREIGN KEY (member_id) REFERENCES members(id))");
        load();
    }

    @Override
    protected long idOf(Fine fine) {
        return fine.getId();
    }

    @Override
    protected void persist(Fine f) {
        db.execute("INSERT OR REPLACE INTO fines (id, transaction_id, member_id, overdue_days, gross_amount, "
                + "waiver_percent, waiver_details, net_amount, breakdown, paid) VALUES (?,?,?,?,?,?,?,?,?,?)",
                f.getId(), f.getTransactionId(), f.getMemberId(), f.getOverdueDays(), f.getGrossAmount(),
                f.getWaiverPercent(), f.getWaiverDetails(), f.getNetAmount(), f.getBreakdown(), f.isPaid() ? 1 : 0);
    }

    @Override
    protected List<Fine> loadAll() {
        return db.query("SELECT * FROM fines ORDER BY id", rs -> {
            Fine f = new Fine(rs.getLong("transaction_id"), rs.getLong("member_id"), rs.getInt("overdue_days"),
                    rs.getDouble("gross_amount"), rs.getDouble("waiver_percent"), rs.getString("waiver_details"),
                    rs.getDouble("net_amount"), rs.getString("breakdown"));
            f.setId(rs.getLong("id"));
            if (rs.getInt("paid") == 1) {
                f.markPaid();
            }
            return f;
        });
    }

    /** Assigns an id and stores the fine. */
    public void add(Fine fine) {
        fine.setId(nextId());
        save(fine);
    }

    public List<Fine> findByMember(long memberId) {
        return findAll().stream().filter(f -> f.getMemberId() == memberId).toList();
    }

    public double unpaidTotal(long memberId) {
        return findByMember(memberId).stream()
                .filter(f -> !f.isPaid())
                .mapToDouble(Fine::getNetAmount)
                .sum();
    }
}
