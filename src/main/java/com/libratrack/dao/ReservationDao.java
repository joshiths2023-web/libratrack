package com.libratrack.dao;

import com.libratrack.model.Reservation;

import java.time.LocalDateTime;
import java.util.List;

/** Storage of reservations (in memory, optionally backed by SQLite). */
public class ReservationDao extends InMemoryDao<Reservation> {

    public ReservationDao() {
        super();
    }

    public ReservationDao(Database db) {
        super(db, "reservations");
        db.execute("CREATE TABLE IF NOT EXISTS reservations (id INTEGER PRIMARY KEY, member_id INTEGER NOT NULL, "
                + "book_id INTEGER NOT NULL, requested_at TEXT NOT NULL, status TEXT NOT NULL, "
                + "FOREIGN KEY (member_id) REFERENCES members(id), FOREIGN KEY (book_id) REFERENCES books(id))");
        load();
    }

    @Override
    protected long idOf(Reservation reservation) {
        return reservation.getId();
    }

    @Override
    protected void persist(Reservation r) {
        db.execute("INSERT OR REPLACE INTO reservations (id, member_id, book_id, requested_at, status) VALUES (?,?,?,?,?)",
                r.getId(), r.getMemberId(), r.getBookId(), r.getRequestedAt().toString(), r.getStatus().name());
    }

    @Override
    protected List<Reservation> loadAll() {
        return db.query("SELECT * FROM reservations ORDER BY id", rs -> {
            Reservation r = new Reservation(rs.getLong("id"), rs.getLong("member_id"), rs.getLong("book_id"),
                    LocalDateTime.parse(rs.getString("requested_at")));
            r.setStatus(Reservation.Status.valueOf(rs.getString("status")));
            return r;
        });
    }

    public List<Reservation> findActiveByBook(long bookId) {
        return findAll().stream().filter(r -> r.isActive() && r.getBookId() == bookId).toList();
    }

    public List<Reservation> findActive() {
        return findAll().stream().filter(Reservation::isActive).toList();
    }
}
