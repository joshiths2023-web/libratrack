package com.libratrack.dao;

import com.libratrack.model.Member;
import com.libratrack.model.MembershipTier;

import java.util.List;

/** Storage of members (in memory, optionally backed by SQLite). */
public class MemberDao extends InMemoryDao<Member> {

    public MemberDao() {
        super();
    }

    public MemberDao(Database db) {
        super(db, "members");
        db.execute("CREATE TABLE IF NOT EXISTS members (id INTEGER PRIMARY KEY, name TEXT NOT NULL, "
                + "tier TEXT NOT NULL, active INTEGER NOT NULL, late_return_count INTEGER NOT NULL)");
        load();
    }

    @Override
    protected long idOf(Member member) {
        return member.getId();
    }

    @Override
    protected void persist(Member m) {
        db.execute("INSERT INTO members (id, name, tier, active, late_return_count) VALUES (?,?,?,?,?) "
                + "ON CONFLICT(id) DO UPDATE SET tier = excluded.tier, active = excluded.active, "
                + "late_return_count = excluded.late_return_count",
                m.getId(), m.getName(), m.getTier().name(), m.isActive() ? 1 : 0, m.getLateReturnCount());
    }

    @Override
    protected List<Member> loadAll() {
        return db.query("SELECT * FROM members ORDER BY id", rs -> {
            Member m = new Member(rs.getLong("id"), rs.getString("name"), MembershipTier.valueOf(rs.getString("tier")));
            m.restoreState(rs.getInt("active") == 1, rs.getInt("late_return_count"));
            return m;
        });
    }
}
