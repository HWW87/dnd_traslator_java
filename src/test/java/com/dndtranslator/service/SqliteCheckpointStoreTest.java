package com.dndtranslator.service;

import com.dndtranslator.service.workflow.CheckpointSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteCheckpointStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesLoadsAndClearsCheckpoint() {
        Path dbPath = tempDir.resolve("checkpoint.db");
        SqliteCheckpointStore store = new SqliteCheckpointStore(dbPath.toString(), 1000, 2);

        CheckpointSnapshot snapshot = new CheckpointSnapshot(
                "job-1",
                "C:/tmp/sample.pdf",
                "Spanish",
                3,
                1,
                false,
                Map.of(0, "texto-0", 1, "texto-1")
        );

        store.save(snapshot);

        Optional<CheckpointSnapshot> loaded = store.load("job-1");
        assertTrue(loaded.isPresent());
        assertEquals("C:/tmp/sample.pdf", loaded.get().pdfPath());
        assertEquals("Spanish", loaded.get().targetLanguage());
        assertEquals(3, loaded.get().paragraphCount());
        assertEquals(1, loaded.get().lastCompletedIndex());
        assertEquals("texto-0", loaded.get().translatedByIndex().get(0));
        assertEquals("texto-1", loaded.get().translatedByIndex().get(1));

        store.clear("job-1");
        assertTrue(store.load("job-1").isEmpty());
    }
}

