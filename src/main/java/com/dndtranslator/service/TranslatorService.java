package com.dndtranslator.service;

import org.json.JSONObject;
import com.dndtranslator.model.TextBlock;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 🌐 Servicio de traducción con segmentación inteligente.
 * Divide texto largo en bloques de ~1000 palabras para evitar truncamientos.
 * Usa modelo Gemma2 por defecto y Llama3.2 como fallback.
 */
public class TranslatorService {

    private static final String PRIMARY_MODEL = "gemma3:1b";
    private static final String FALLBACK_MODEL = "llama3.2:1b-instruct";
    private static final String OLLAMA_URL = "http://localhost:11434/api/generate";
    private static final String CACHE_DB = "translations.db";
    private static final int DEFAULT_MAX_THREADS = Math.max(1, Runtime.getRuntime().availableProcessors());
    private static final int RETRY_COUNT = 2;
    private static final int CACHE_BUSY_TIMEOUT_MS = 5000;
    private static final int CACHE_WRITE_RETRIES = 2;

    private final int maxThreads = resolveParallelism();
    private final ExecutorService executor = Executors.newFixedThreadPool(maxThreads);
    private final HttpClient client = HttpClient.newHttpClient();
    private final ReentrantLock cacheWriteLock = new ReentrantLock();

    public TranslatorService() {
        initCache();
        System.out.println("[TranslatorService] Hilos de traduccion configurados: " + maxThreads);
    }

    // ===========================================================
    // 🔹 Traducción multi-hilo de bloques (como antes)
    // ===========================================================
    public List<String> translateBlocks(List<TextBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) return Collections.emptyList();

        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (TextBlock block : blocks) {
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() ->
                    translate(block.getText(), "Spanish"), executor);
            futures.add(future);
        }

        List<String> results = new ArrayList<>();
        for (CompletableFuture<String> f : futures) {
            try {
                results.add(f.get());
            } catch (Exception e) {
                results.add("[Error al traducir bloque]");
                System.err.println("⚠️ Error en bloque: " + e.getMessage());
            }
        }

        return results;
    }

    // ===========================================================
    // 🔹 Traducción individual con segmentación
    // ===========================================================
    public String translate(String text, String targetLanguage) {
        if (text == null || text.isBlank()) return "";

        // 🔸 Revisión de caché
        /*String cached = getCachedTranslation(text);
        if (cached != null) return cached;*/

        // 🔸 Detección del modelo disponible
        String model = getAvailableModel();
        if (model == null) {
            System.err.println("⚠️ Ningún modelo Ollama disponible. Ejecute 'ollama serve'.");
            return "[Error: Ollama no disponible]";
        }

        // 🔸 Segmentar el texto en bloques más pequeños
        List<String> segments = segmentText(text, 1000); // ≈1500 tokens
        StringBuilder translatedTotal = new StringBuilder();

        for (String segment : segments) {
            String translatedSegment = translateSegment(segment, targetLanguage, model);
            translatedTotal.append(translatedSegment).append("\n");
        }

        String translatedFull = translatedTotal.toString().trim();
        cacheTranslation(text, translatedFull);
        return translatedFull;
    }

    // ===========================================================
    // 🔹 Segmenta texto en bloques de n palabras aprox.
    // ===========================================================
    private List<String> segmentText(String text, int maxWords) {
        List<String> segments = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            current.append(word).append(" ");
            if (current.toString().split("\\s+").length >= maxWords) {
                segments.add(current.toString().trim());
                current.setLength(0);
            }
        }

        if (!current.isEmpty()) segments.add(current.toString().trim());
        return segments;
    }

    // ===========================================================
    // 🔹 Traduce un solo fragmento con reintentos
    // ===========================================================
    private String translateSegment(String text, String targetLanguage, String model) {
        for (int attempt = 1; attempt <= RETRY_COUNT; attempt++) {
            try {
                String prompt = String.format("""
                        Translate the following text *directly* to %s.
                        Rules:
                        - Do NOT include explanations, notes, or introductions.
                        - Preserve proper names and RPG terminology.
                        - Maintain line breaks and paragraph structure.
                        - Output ONLY the translated text.
                        
                        %s
                        """, targetLanguage, text);

                JSONObject body = new JSONObject()
                        .put("model", model)
                        .put("prompt", prompt)
                        .put("stream", false);

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(OLLAMA_URL))
                        .header("Content-Type", "application/json; charset=utf-8")
                        .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                        .build();

                HttpResponse<String> response =
                        client.send(request, HttpResponse.BodyHandlers.ofString(Charset.forName("UTF-8")));
                if (response.statusCode() == 200) {
                    String translated = parseOllamaResponse(response.body());
                    return cleanTranslation(translated);
                }

            } catch (IOException | InterruptedException e) {
                System.err.println("⚠️ Intento " + attempt + " falló: " + e.getMessage());
            }
        }
        return "[Error: segmento no traducido]";
    }

    // ===========================================================
    // 🔹 Limpia frases no deseadas del modelo
    // ===========================================================
    private String cleanTranslation(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(?i)here is the translation[:\\-\\s]*", "")
                .replaceAll("(?i)note[:\\-\\s]*", "")
                .replaceAll("(?i)i hope this helps.*", "")
                .trim();
    }

    // ===========================================================
    // 🔹 Detección de modelo disponible
    // ===========================================================
    private String getAvailableModel() {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:11434/api/tags"))
                    .GET().build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() != 200) return null;
            String body = res.body();

            if (body.contains(PRIMARY_MODEL.split(":")[0])) return PRIMARY_MODEL;
            if (body.contains(FALLBACK_MODEL.split(":")[0])) return FALLBACK_MODEL;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    // ===========================================================
    // 🔹 Parseo y Cache (igual que antes)
    // ===========================================================
    private String parseOllamaResponse(String body) {
        try {
            JSONObject jo = new JSONObject(body);
            return jo.get("response").toString();
        } catch (Exception e) {
            return body.trim();
        }
    }

    private void initCache() {
        try (Connection conn = openCacheConnection();
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
            System.err.println("⚠️ Error creando cache DB: " + e.getMessage());
        }
    }

    private String getCachedTranslation(String original) {
        try (Connection conn = openCacheConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT translated FROM translations WHERE original = ?")) {
            ps.setString(1, original);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("translated");
        } catch (SQLException ignored) {
        }
        return null;
    }

    private void cacheTranslation(String original, String translated) {
        cacheWriteLock.lock();
        try {
            for (int attempt = 1; attempt <= CACHE_WRITE_RETRIES; attempt++) {
                try (Connection conn = openCacheConnection();
                     PreparedStatement ps = conn.prepareStatement(
                             "INSERT OR IGNORE INTO translations(original, translated, model, created_at) VALUES (?, ?, ?, ?)")) {
                    ps.setString(1, original);
                    ps.setString(2, translated);
                    ps.setString(3, PRIMARY_MODEL);
                    ps.setString(4, LocalDateTime.now().toString());
                    ps.executeUpdate();
                    return;
                } catch (SQLException e) {
                    boolean busy = isSqliteBusy(e);
                    if (!busy || attempt == CACHE_WRITE_RETRIES) {
                        System.err.println("⚠️ Cache insert failed: " + e.getMessage());
                        return;
                    }
                    try {
                        Thread.sleep(80L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        } finally {
            cacheWriteLock.unlock();
        }
    }

    private Connection openCacheConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + CACHE_DB);
        try (Statement pragma = conn.createStatement()) {
            pragma.execute("PRAGMA journal_mode=WAL");
            pragma.execute("PRAGMA synchronous=NORMAL");
            pragma.execute("PRAGMA busy_timeout=" + CACHE_BUSY_TIMEOUT_MS);
        }
        return conn;
    }

    private boolean isSqliteBusy(SQLException e) {
        String msg = e.getMessage();
        return msg != null && msg.toUpperCase(Locale.ROOT).contains("SQLITE_BUSY");
    }

    private int resolveParallelism() {
        String raw = System.getenv("DND_MAX_THREADS");
        if (raw == null || raw.isBlank()) {
            return DEFAULT_MAX_THREADS;
        }
        try {
            int configured = Integer.parseInt(raw.trim());
            if (configured < 1) {
                return DEFAULT_MAX_THREADS;
            }
            return configured;
        } catch (NumberFormatException e) {
            return DEFAULT_MAX_THREADS;
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
