package com.libratrack.dao;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Thin wrapper around a single SQLite JDBC connection. All access is serialised. */
public class Database implements AutoCloseable {

    /** Maps one result-set row to an object. */
    @FunctionalInterface
    public interface RowMapper<T> {
        T map(ResultSet rs) throws SQLException;
    }

    private final Connection connection;

    private Database(Connection connection) {
        this.connection = connection;
    }

    /** Opens (creating if needed) the SQLite file at {@code path}; use ":memory:" for a throw-away DB. */
    public static Database open(String path) {
        try {
            Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
            try (Statement st = c.createStatement()) {
                st.execute("PRAGMA foreign_keys = ON");
            }
            return new Database(c);
        } catch (SQLException e) {
            throw new DataAccessException("Cannot open database " + path, e);
        }
    }

    public synchronized void execute(String sql, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("SQL failed: " + sql, e);
        }
    }

    public synchronized <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            bind(ps, params);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(mapper.map(rs));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new DataAccessException("SQL failed: " + sql, e);
        }
    }

    public synchronized long maxId(String table) {
        return query("SELECT COALESCE(MAX(id), 0) FROM " + table, rs -> rs.getLong(1)).get(0);
    }

    private static void bind(PreparedStatement ps, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new DataAccessException("Cannot close database", e);
        }
    }

    /** Unchecked wrapper for JDBC failures. */
    public static class DataAccessException extends RuntimeException {
        public DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
