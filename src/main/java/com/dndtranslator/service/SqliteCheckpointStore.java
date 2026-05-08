package com.dndtranslator.service;

import com.dndtranslator.service.workflow.CheckpointSnapshot;
import com.dndtranslator.service.workflow.CheckpointStore;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementacion SQLite para guardar checkpoints de traduccion.
 */
@SuppressWarnings({"SqlNoDataSourceInspection", "SqlResolve", "SqlDialectInspection"})
public class SqliteCheckpointStore implements CheckpointStore {

    private static final Logger logger = LoggerFactory.getLogger(SqliteCheckpointStore.class);

    private static final String DEFAULT_DB_PATH = "translations.db";
    private static final int DEFAULT_BUSY_TIMEOUT_MS = 5000;
    private static final int DEFAULT_WRITE_RETRIES = 2;

    private final String dbPath;
    private final int busyTimeoutMs;
    private final int writeRetries;
    private final ReentrantLock writeLock = new ReentrantLock();

    public SqliteCheckpointStore() {
        this(resolveDbPath(), DEFAULT_BUSY_TIMEOUT_MS, DEFAULT_WRITE_RETRIES);
    }

    public SqliteCheckpointStore(String dbPath, int busyTimeoutMs, int writeRetries) {
        this.dbPath = dbPath;
        this.busyTimeoutMs = busyTimeoutMs;
        this.writeRetries = Math.max(1, writeRetries);
        initTable();
    }

    @Override
    public Optional<CheckpointSnapshot> load(String jobKey) {
        if (jobKey == null || jobKey.isBlank()) {
            return Optional.empty();
        }

        try (Connection conn = openConnection();
             //noinspection SqlNoDataSourceInspection
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT pdf_path, target_language, paragraph_count, last_completed_index,
                            used_ocr_fallback, translated_payload
                     FROM translation_checkpoints
                     WHERE job_key = ?
                     """)) {
            ps.setString(1, jobKey);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                return Optional.empty();
            }

            String pdfPath = rs.getString("pdf_path");
            String targetLanguage = rs.getString("target_language");
            int paragraphCount = rs.getInt("paragraph_count");
            int lastCompletedIndex = rs.getInt("last_completed_index");
            boolean usedOcrFallback = rs.getInt("used_ocr_fallback") == 1;
            String payload = rs.getString("translated_payload");

            return Optional.of(new CheckpointSnapshot(
                    jobKey,
                    pdfPath,
                    targetLanguage,
                    paragraphCount,
                    lastCompletedIndex,
                    usedOcrFallback,
                    deserializeTranslations(payload)
            ));
        } catch (SQLException e) {
            logger.warn("Error leyendo checkpoint {}: {}", jobKey, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(CheckpointSnapshot snapshot) {
        if (snapshot == null || snapshot.jobKey() == null || snapshot.jobKey().isBlank()) {
            return;
        }

        writeLock.lock();
        try {
            for (int attempt = 1; attempt <= writeRetries; attempt++) {
                try (Connection conn = openConnection();
                     //noinspection SqlNoDataSourceInspection
                     PreparedStatement ps = conn.prepareStatement("""
                             INSERT OR REPLACE INTO translation_checkpoints(
                                 job_key, pdf_path, target_language, paragraph_count,
                                 last_completed_index, used_ocr_fallback, translated_payload, updated_at
                             ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                             """)) {
                    ps.setString(1, snapshot.jobKey());
                    ps.setString(2, snapshot.pdfPath());
                    ps.setString(3, snapshot.targetLanguage());
                    ps.setInt(4, snapshot.paragraphCount());
                    ps.setInt(5, snapshot.lastCompletedIndex());
                    ps.setInt(6, snapshot.usedOcrFallback() ? 1 : 0);
                    ps.setString(7, serializeTranslations(snapshot.translatedByIndex()));
                    ps.setString(8, LocalDateTime.now().toString());
                    ps.executeUpdate();
                    return;
                } catch (SQLException e) {
                    boolean busy = isSqliteBusy(e);
                    if (!busy || attempt == writeRetries) {
                        logger.error("Error guardando checkpoint {}: {}", snapshot.jobKey(), e.getMessage());
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

    @Override
    public void clear(String jobKey) {
        if (jobKey == null || jobKey.isBlank()) {
            return;
        }

        try (Connection conn = openConnection();
             //noinspection SqlNoDataSourceInspection
             PreparedStatement ps = conn.prepareStatement("DELETE FROM translation_checkpoints WHERE job_key = ?")) {
            ps.setString(1, jobKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warn("No se pudo borrar checkpoint {}: {}", jobKey, e.getMessage());
        }
    }

    private void initTable() {
        try (Connection conn = openConnection();
             Statement stmt = conn.createStatement()) {
            //noinspection SqlNoDataSourceInspection
            stmt.execute("""
                    CREATE TABLE IF NOT EXISTS translation_checkpoints (
                        job_key TEXT PRIMARY KEY,
                        pdf_path TEXT NOT NULL,
                        target_language TEXT NOT NULL,
                        paragraph_count INTEGER NOT NULL,
                        last_completed_index INTEGER NOT NULL,
                        used_ocr_fallback INTEGER NOT NULL,
                        translated_payload TEXT NOT NULL,
                        updated_at TEXT NOT NULL
                    )
                    """);
        } catch (SQLException e) {
            logger.error("Error creando tabla translation_checkpoints: {}", e.getMessage());
        }
    }

    private Connection openConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement pragma = conn.createStatement()) {
            //noinspection SqlNoDataSourceInspection
            pragma.execute("PRAGMA journal_mode=WAL");
            //noinspection SqlNoDataSourceInspection
            pragma.execute("PRAGMA synchronous=NORMAL");
            //noinspection SqlNoDataSourceInspection
            pragma.execute("PRAGMA busy_timeout=" + busyTimeoutMs);
        }
        return conn;
    }

    private boolean isSqliteBusy(SQLException e) {
        String message = e.getMessage();
        return message != null && message.toUpperCase(Locale.ROOT).contains("SQLITE_BUSY");
    }

    private static String resolveDbPath() {
        String configured = System.getenv("DND_CHECKPOINT_DB_PATH");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_DB_PATH;
        }
        return configured.trim();
    }

    private String serializeTranslations(Map<Integer, String> translatedByIndex) {
        JSONArray items = new JSONArray();
        if (translatedByIndex != null) {
            for (Map.Entry<Integer, String> entry : translatedByIndex.entrySet()) {
                if (entry.getValue() == null || entry.getValue().isBlank()) {
                    continue;
                }
                items.put(new JSONObject()
                        .put("index", entry.getKey())
                        .put("text", entry.getValue()));
            }
        }
        return items.toString();
    }

    private Map<Integer, String> deserializeTranslations(String payload) {
        Map<Integer, String> translatedByIndex = new HashMap<>();
        if (payload == null || payload.isBlank()) {
            return translatedByIndex;
        }

        try {
            JSONArray items = new JSONArray(payload);
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                int index = item.optInt("index", -1);
                String text = item.optString("text", "");
                if (index >= 0 && !text.isBlank()) {
                    translatedByIndex.put(index, text);
                }
            }
        } catch (Exception e) {
            logger.warn("No se pudo parsear payload de checkpoint: {}", e.getMessage());
        }

        return translatedByIndex;
    }
}

