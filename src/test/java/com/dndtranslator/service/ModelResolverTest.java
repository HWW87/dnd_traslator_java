package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModelResolverTest {

    @Test
    void resolvesPrimaryWhenAvailable() {
        ModelResolver resolver = new ModelResolver("gemma3:1b", "llama3.2:1b-instruct");

        String payload = "{\"models\":[{\"name\":\"gemma3:1b\"},{\"name\":\"llama3.2:1b-instruct\"}]}";

        assertEquals("gemma3:1b", resolver.resolveModel(payload));
    }

    @Test
    void resolvesFallbackWhenPrimaryMissing() {
        ModelResolver resolver = new ModelResolver("gemma3:1b", "llama3.2:1b-instruct");

        String payload = "{\"models\":[{\"name\":\"llama3.2:1b-instruct\"}]}";

        assertEquals("llama3.2:1b-instruct", resolver.resolveModel(payload));
    }

    @Test
    void returnsNullWhenNoKnownModel() {
        ModelResolver resolver = new ModelResolver("gemma3:1b", "llama3.2:1b-instruct");

        assertNull(resolver.resolveModel("{\"models\":[{\"name\":\"mistral\"}]}"));
    }
}

