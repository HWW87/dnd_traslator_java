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

    public Optional<String> findTranslation(TranslationCacheKey key) {
        if (key == null) {
            return Optional.empty();
        }

        Optional<String> versioned = findTranslation(key.asStorageKey());
        if (versioned.isPresent()) {
            return versioned;
        }

        // Compatibilidad: solo usar fallback legacy para claves sin metadata explícita.
        if (!key.isVersionedMetadataPresent()) {
            return findTranslation(key.asLegacyStorageKey());
        }

        return Optional.empty();
    }

    public Optional<String> findTranslation(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT translated FROM translations WHERE original = ? AND (invalidated_at IS NULL OR invalidated_at = '')")) {
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

    public void saveTranslation(TranslationCacheKey key, String translatedText) {
        if (key == null) {
            return;
        }
        saveTranslation(key.asStorageKey(), translatedText, key.modelName(), CacheMetadata.fromKey(key, "unknown"));
    }

    public void saveTranslation(TranslationCacheKey key, String translatedText, String providerId) {
        if (key == null) {
            return;
        }
        saveTranslation(key.asStorageKey(), translatedText, key.modelName(), CacheMetadata.fromKey(key, providerId));
    }

    public void saveTranslation(String key, String translatedText) {
        saveTranslation(key, translatedText, "unknown");
    }

    public void saveTranslation(String key, String translatedText, String model) {
        saveTranslation(key, translatedText, model, null);
    }

    public void saveTranslation(String key, String translatedText, String model, CacheMetadata metadata) {
        if (key == null || key.isBlank() || translatedText == null || translatedText.isBlank()) {
            return;
        }

        writeLock.lock();
        try {
            for (int attempt = 1; attempt <= writeRetries; attempt++) {
                try (Connection conn = openConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             """
                             INSERT OR REPLACE INTO translations(
                                 original, translated, model, created_at,
                                 provider_id, strategy_version, sanitizer_version, validator_version,
                                 status, confidence, invalidated_at
                             ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)
                             """)) {
                    CacheMetadata effectiveMetadata = metadata == null
                            ? new CacheMetadata("unknown", "unknown", "unknown", "unknown", "active", null, LocalDateTime.now().toString())
                            : metadata;

                    ps.setString(1, key);
                    ps.setString(2, translatedText);
                    ps.setString(3, model == null || model.isBlank() ? "unknown" : model);
                    ps.setString(4, effectiveMetadata.createdAt());
                    ps.setString(5, effectiveMetadata.providerId());
                    ps.setString(6, effectiveMetadata.strategyVersion());
                    ps.setString(7, effectiveMetadata.sanitizerVersion());
                    ps.setString(8, effectiveMetadata.validatorVersion());
                    ps.setString(9, effectiveMetadata.status());
                    if (effectiveMetadata.confidence() == null) {
                        ps.setNull(10, java.sql.Types.REAL);
                    } else {
                        ps.setDouble(10, effectiveMetadata.confidence());
                    }
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
                    created_at TEXT,
                    provider_id TEXT,
                    strategy_version TEXT,
                    sanitizer_version TEXT,
                    validator_version TEXT,
                    status TEXT,
                    confidence REAL,
                    invalidated_at TEXT
                )
            """);
            ensureColumn(stmt, "translations", "provider_id", "TEXT");
            ensureColumn(stmt, "translations", "strategy_version", "TEXT");
            ensureColumn(stmt, "translations", "sanitizer_version", "TEXT");
            ensureColumn(stmt, "translations", "validator_version", "TEXT");
            ensureColumn(stmt, "translations", "status", "TEXT");
            ensureColumn(stmt, "translations", "confidence", "REAL");
            ensureColumn(stmt, "translations", "invalidated_at", "TEXT");
        } catch (SQLException e) {
            logger.error("Error creando cache DB: {}", e.getMessage());
        }
    }

    public int invalidateByStrategyVersion(String strategyVersion) {
        if (strategyVersion == null || strategyVersion.isBlank()) {
            return 0;
        }
        return invalidateWhere("strategy_version = ?", strategyVersion.trim().toLowerCase(Locale.ROOT));
    }

    public int invalidateLegacyEntries() {
        return invalidateWhere("(strategy_version IS NULL OR strategy_version = '' OR strategy_version = 'unknown')");
    }

    public int invalidateAll() {
        return invalidateWhere("1 = 1");
    }

    private int invalidateWhere(String whereClause, String... params) {
        writeLock.lock();
        try (Connection conn = openConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE translations SET invalidated_at = ? WHERE (invalidated_at IS NULL OR invalidated_at = '') AND " + whereClause)) {
            ps.setString(1, LocalDateTime.now().toString());
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 2, params[i]);
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("No se pudo invalidar cache: {}", e.getMessage());
            return 0;
        } finally {
            writeLock.unlock();
        }
    }

    private void ensureColumn(Statement stmt, String tableName, String columnName, String columnType) {
        try {
            stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnType);
        } catch (SQLException ignored) {
            // Columna ya existe o DB antigua no requiere cambio.
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

