package com.dndtranslator.service;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class OllamaClient {

    private static final Logger logger = LoggerFactory.getLogger(OllamaClient.class);

    private static final String DEFAULT_GENERATE_URL = "http://localhost:11434/api/generate";
    private static final String DEFAULT_TAGS_URL = "http://localhost:11434/api/tags";
    private static final long DEFAULT_TIMEOUT_SECONDS = 60L;

    private final HttpClient httpClient;
    private final URI generateUri;
    private final URI tagsUri;
    private final Duration requestTimeout;

    public OllamaClient() {
        this(
                HttpClient.newHttpClient(),
                resolveUrl("DND_OLLAMA_GENERATE_URL", DEFAULT_GENERATE_URL),
                resolveUrl("DND_OLLAMA_TAGS_URL", DEFAULT_TAGS_URL),
                Duration.ofSeconds(resolveTimeoutSeconds())
        );
    }

    public OllamaClient(HttpClient httpClient, String generateUrl, String tagsUrl, Duration requestTimeout) {
        this.httpClient = httpClient;
        this.generateUri = URI.create(generateUrl);
        this.tagsUri = URI.create(tagsUrl);
        this.requestTimeout = requestTimeout;
    }

    public String translate(String model, String prompt) throws IOException, InterruptedException {
        JSONObject body = new JSONObject()
                .put("model", model)
                .put("prompt", prompt)
                .put("stream", false);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(generateUri)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("Ollama devolvio status " + response.statusCode());
        }

        return parseResponse(response.body());
    }

    public Optional<String> fetchAvailableModelsPayload() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(tagsUri)
                .timeout(requestTimeout)
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                logger.warn("Ollama /api/tags devolvio status {}", response.statusCode());
                return Optional.empty();
            }
            String body = response.body();
            return body == null || body.isBlank() ? Optional.empty() : Optional.of(body);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException e) {
            logger.warn("Error consultando /api/tags de Ollama: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<String> fetchAvailableModels() {
        Optional<String> payload = fetchAvailableModelsPayload();
        if (payload.isEmpty()) {
            return Collections.emptyList();
        }
        return parseAvailableModels(payload.get());
    }

    private String parseResponse(String body) {
        if (body == null) {
            return "";
        }
        try {
            JSONObject json = new JSONObject(body);
            return json.optString("response", "").trim();
        } catch (Exception ignored) {
            return body.trim();
        }
    }

    private List<String> parseAvailableModels(String body) {
        if (body == null || body.isBlank()) {
            return Collections.emptyList();
        }

        try {
            JSONObject json = new JSONObject(body);
            List<String> names = new ArrayList<>();
            for (Object rawModel : json.optJSONArray("models")) {
                if (!(rawModel instanceof JSONObject modelObject)) {
                    continue;
                }
                String name = modelObject.optString("name", modelObject.optString("model", "")).trim();
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
            return List.copyOf(names);
        } catch (Exception e) {
            logger.warn("No se pudo parsear la lista de modelos de Ollama: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private static String resolveUrl(String envVar, String defaultValue) {
        String configured = System.getenv(envVar);
        if (configured == null || configured.isBlank()) {
            return defaultValue;
        }
        return configured.trim();
    }

    private static long resolveTimeoutSeconds() {
        String configured = System.getenv("DND_OLLAMA_TIMEOUT_SECONDS");
        if (configured == null || configured.isBlank()) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
        try {
            long parsed = Long.parseLong(configured.trim());
            return parsed > 0 ? parsed : DEFAULT_TIMEOUT_SECONDS;
        } catch (NumberFormatException ignored) {
            return DEFAULT_TIMEOUT_SECONDS;
        }
    }
}

