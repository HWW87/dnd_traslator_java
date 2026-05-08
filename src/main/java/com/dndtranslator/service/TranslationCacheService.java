package com.dndtranslator.service;

import java.sql.*;

/**
 * @deprecated Use {@link TranslationCacheRepository} instead.
 *
 * This class is a legacy implementation of translation caching.
 * It has been superseded by TranslationCacheRepository which provides
 * better integration with the domain model and workflow.
 *
 * Phase 0: Baseline Audit and Cleanup
 * Target removal: Phase 3 or later
 */
@Deprecated(since = "1.0.1", forRemoval = true)
public class TranslationCacheService {
    private final Connection conn;

    public TranslationCacheService() throws SQLException {
        conn = DriverManager.getConnection("jdbc:sqlite:translations.db");
        conn.createStatement().execute("""
            CREATE TABLE IF NOT EXISTS cache (
              text TEXT PRIMARY KEY,
              translated TEXT
            )
        """);
    }

    public String getCached(String text) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("SELECT translated FROM cache WHERE text = ?");
        ps.setString(1, text);
        ResultSet rs = ps.executeQuery();
        return rs.next() ? rs.getString("translated") : null;
    }

    public void save(String text, String translated) throws SQLException {
        PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO cache VALUES (?, ?)");
        ps.setString(1, text);
        ps.setString(2, translated);
        ps.executeUpdate();
    }
}
