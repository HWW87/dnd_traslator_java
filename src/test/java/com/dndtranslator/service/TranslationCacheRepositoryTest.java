package com.dndtranslator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationCacheRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void savesAndReadsTranslation() {
        Path dbPath = tempDir.resolve("cache.db");
        TranslationCacheRepository repository = new TranslationCacheRepository(dbPath.toString(), 1000, 2);

        repository.saveTranslation("original text", "texto traducido", "gemma3:1b");
        Optional<String> found = repository.findTranslation("original text");

        assertTrue(found.isPresent());
        assertEquals("texto traducido", found.get());
    }

    @Test
    void returnsEmptyWhenTranslationDoesNotExist() {
        Path dbPath = tempDir.resolve("cache.db");
        TranslationCacheRepository repository = new TranslationCacheRepository(dbPath.toString(), 1000, 2);

        Optional<String> found = repository.findTranslation("missing");

        assertTrue(found.isEmpty());
    }
}

