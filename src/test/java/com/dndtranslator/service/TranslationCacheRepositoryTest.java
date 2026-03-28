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
    void savesAndReadsTranslationWithCompositeCacheKey() {
        Path dbPath = tempDir.resolve("cache.db");
        TranslationCacheRepository repository = new TranslationCacheRepository(dbPath.toString(), 1000, 2);

        TranslationCacheKey key = new TranslationCacheKey(
                "original text",
                "Spanish",
                "gemma3:1b",
                "translator-v1"
        );

        repository.saveTranslation(key, "texto traducido");
        Optional<String> found = repository.findTranslation(key);

        assertTrue(found.isPresent());
        assertEquals("texto traducido", found.get());
    }

    @Test
    void returnsDifferentEntriesForDifferentTargetLanguage() {
        Path dbPath = tempDir.resolve("cache.db");
        TranslationCacheRepository repository = new TranslationCacheRepository(dbPath.toString(), 1000, 2);

        TranslationCacheKey spanish = new TranslationCacheKey("attack", "Spanish", "gemma3:1b", "translator-v1");
        TranslationCacheKey french = new TranslationCacheKey("attack", "French", "gemma3:1b", "translator-v1");

        repository.saveTranslation(spanish, "ataque");
        repository.saveTranslation(french, "attaque");

        assertEquals("ataque", repository.findTranslation(spanish).orElse(""));
        assertEquals("attaque", repository.findTranslation(french).orElse(""));
    }

    @Test
    void returnsDifferentEntriesForDifferentModelOrStrategyVersion() {
        Path dbPath = tempDir.resolve("cache.db");
        TranslationCacheRepository repository = new TranslationCacheRepository(dbPath.toString(), 1000, 2);

        TranslationCacheKey modelA = new TranslationCacheKey("attack", "Spanish", "gemma3:1b", "translator-v1");
        TranslationCacheKey modelB = new TranslationCacheKey("attack", "Spanish", "llama3:8b", "translator-v1");
        TranslationCacheKey strategyV2 = new TranslationCacheKey("attack", "Spanish", "gemma3:1b", "translator-v2");

        repository.saveTranslation(modelA, "ataque a");
        repository.saveTranslation(modelB, "ataque b");
        repository.saveTranslation(strategyV2, "ataque v2");

        assertEquals("ataque a", repository.findTranslation(modelA).orElse(""));
        assertEquals("ataque b", repository.findTranslation(modelB).orElse(""));
        assertEquals("ataque v2", repository.findTranslation(strategyV2).orElse(""));
    }

    @Test
    void returnsEmptyWhenTranslationDoesNotExist() {
        Path dbPath = tempDir.resolve("cache.db");
        TranslationCacheRepository repository = new TranslationCacheRepository(dbPath.toString(), 1000, 2);

        TranslationCacheKey key = new TranslationCacheKey("missing", "Spanish", "gemma3:1b", "translator-v1");
        Optional<String> found = repository.findTranslation(key);

        assertTrue(found.isEmpty());
    }
}
