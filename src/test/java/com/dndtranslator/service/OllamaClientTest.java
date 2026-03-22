package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OllamaClientTest {

    @Test
    void translateParsesResponseBody() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"response\":\"hola\"}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        OllamaClient client = new OllamaClient(
                httpClient,
                "http://localhost:11434/api/generate",
                "http://localhost:11434/api/tags",
                Duration.ofSeconds(5)
        );

        String translated = client.translate("gemma3:1b", "prompt");

        assertEquals("hola", translated);
    }

    @Test
    void translateThrowsForNonSuccessStatus() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(500);
        when(response.body()).thenReturn("error");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        OllamaClient client = new OllamaClient(
                httpClient,
                "http://localhost:11434/api/generate",
                "http://localhost:11434/api/tags",
                Duration.ofSeconds(5)
        );

        assertThrows(IOException.class, () -> client.translate("gemma3:1b", "prompt"));
    }

    @Test
    void fetchAvailableModelsReturnsEmptyWhenRequestFails() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("network down"));

        OllamaClient client = new OllamaClient(
                httpClient,
                "http://localhost:11434/api/generate",
                "http://localhost:11434/api/tags",
                Duration.ofSeconds(5)
        );

        Optional<String> payload = client.fetchAvailableModelsPayload();

        assertTrue(payload.isEmpty());
    }

    @Test
    void fetchAvailableModelsParsesNamesFromTagsPayload() throws Exception {
        HttpClient httpClient = mock(HttpClient.class);
        HttpResponse<String> response = mock(HttpResponse.class);

        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"models\":[{\"name\":\"translategemma:12b\"},{\"name\":\"translategemma:4b-q4\"}]}");
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        OllamaClient client = new OllamaClient(
                httpClient,
                "http://localhost:11434/api/generate",
                "http://localhost:11434/api/tags",
                Duration.ofSeconds(5)
        );

        List<String> models = client.fetchAvailableModels();

        assertEquals(List.of("translategemma:12b", "translategemma:4b-q4"), models);
    }
}

