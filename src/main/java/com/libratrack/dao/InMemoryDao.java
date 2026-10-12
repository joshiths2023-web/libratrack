package com.libratrack.dao;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for the data access objects. Entities are held in a map (the working set); when a
 * {@link Database} is supplied every {@link #save} is also written through to SQLite and the map is
 * pre-loaded from it at start-up. Without a database it behaves as a purely in-memory store.
 */
public abstract class InMemoryDao<T> {
    private final Map<Long, T> store = new LinkedHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);
    protected final Database db;
    private final String table;

    protected InMemoryDao() {
        this(null, null);
    }

    protected InMemoryDao(Database db, String table) {
        this.db = db;
        this.table = table;
    }

    protected abstract long idOf(T entity);

    /** Writes (insert or update) one entity to the database. Only called when a database is present. */
    protected void persist(T entity) {
        // in-memory subclasses have nothing to persist
    }

    /** Reads every stored entity from the database. Only called when a database is present. */
    protected List<T> loadAll() {
        return List.of();
    }

    /** Loads existing rows and resumes the id sequence. Subclasses with a database call this once, last in their constructor. */
    protected final void load() {
        if (db == null) {
            return;
        }
        for (T entity : loadAll()) {
            store.put(idOf(entity), entity);
        }
        sequence.set(db.maxId(table) + 1);
    }

    public long nextId() {
        return sequence.getAndIncrement();
    }

    public synchronized void save(T entity) {
        store.put(idOf(entity), entity);
        if (db != null) {
            persist(entity);
        }
    }

    public synchronized Optional<T> findById(long id) {
        return Optional.ofNullable(store.get(id));
    }

    public synchronized List<T> findAll() {
        return new ArrayList<>(store.values());
    }

    public synchronized void clear() {
        store.clear();
        if (db != null) {
            db.execute("DELETE FROM " + table);
        }
    }
}
