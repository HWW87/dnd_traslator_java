package com.dndtranslator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public class TranslationCacheRepository {

    private static final Logger logger = LoggerFactory.getLogger(TranslationCacheRepository.class);

    private static final String DEFAULT_DB_PATH = "translations.db";
    private static final int DEFAULT_BUSY_TIMEOUT_MS = 5000;
    private static final int DEFAULT_WRITE_RETRIES = 2;

    private final String dbPath;
    private final int busyTimeoutMs;
    private final int writeRetries;
    private final ReentrantLock writeLock = new ReentrantLock();

    public TranslationCacheRepository() {
        this(resolveDbPath(), DEFAULT_BUSY_TIMEOUT_MS, DEFAULT_WRITE_RETRIES);
    }

    public TranslationCacheRepository(String dbPath, int busyTimeoutMs, int writeRetries) {
        this.dbPath = dbPath;
        this.busyTimeoutMs = busyTimeoutMs;
        this.writeRetries = Math.max(1, writeRetries);
        initCache();
    }

    public Optional<String> findTranslation(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT translated FROM translations WHERE original = ?")) {
            ps.setString(1, key);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return Optional.ofNullable(rs.getString("translated"));
            }
        } catch (SQLException e) {
            logger.warn("Error leyendo cache de traducciones: {}", e.getMessage());
        }

        return Optional.empty();
    }

    public void saveTranslation(String key, String translatedText) {
        saveTranslation(key, translatedText, "unknown");
    }

    public void saveTranslation(String key, String translatedText, String model) {
        if (key == null || key.isBlank() || translatedText == null || translatedText.isBlank()) {
            return;
        }

        writeLock.lock();
        try {
            for (int attempt = 1; attempt <= writeRetries; attempt++) {
                try (Connection conn = openConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT OR IGNORE INTO translations(original, translated, model, created_at) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, key);
                    ps.setString(2, translatedText);
                    ps.setString(3, model == null || model.isBlank() ? "unknown" : model);
                    ps.setString(4, LocalDateTime.now().toString());
                    ps.executeUpdate();
                    return;
                } catch (SQLException e) {
                    boolean busy = isSqliteBusy(e);
                    if (!busy || attempt == writeRetries) {
                        logger.error("Cache insert failed: {}", e.getMessage());
                        return;
                    }
                    try {
                        Thread.sleep(80L * attempt);
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        } finally {
            writeLock.unlock();
        }
    }

    private void initCache() {
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS translations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    original TEXT UNIQUE,
                    translated TEXT,
                    model TEXT,
                    created_at TEXT
                )
            """);
        } catch (SQLException e) {
            logger.error("Error creando cache DB: {}", e.getMessage());
        }
    }

    private Connection openConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement pragma = conn.createStatement()) {
            pragma.execute("PRAGMA journal_mode=WAL");
            pragma.execute("PRAGMA synchronous=NORMAL");
            pragma.execute("PRAGMA busy_timeout=" + busyTimeoutMs);
        }
        return conn;
    }

    private boolean isSqliteBusy(SQLException e) {
        String message = e.getMessage();
        return message != null && message.toUpperCase(Locale.ROOT).contains("SQLITE_BUSY");
    }

    private static String resolveDbPath() {
        String configured = System.getenv("DND_CACHE_DB_PATH");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_DB_PATH;
        }
        return configured.trim();
    }
}

