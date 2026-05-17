package com.dndtranslator.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para Checkpoint granular a nivel de TranslationUnit - Phase 10
 */
public class CheckpointUnitTrackingTest {

    @Test
    public void testCheckpointRecordsCompletedUnits() {
        Checkpoint checkpoint = new Checkpoint("job-1", 1, null, "ollama");

        // Crear y traducir algunas unidades
        TranslationUnit unit1 = new TranslationUnit(1, "Text 1", UnitType.PARAGRAPH, "Spanish");
        TranslationUnit unit2 = new TranslationUnit(1, "Text 2", UnitType.PARAGRAPH, "Spanish");

        unit1.markTranslated("Texto 1");
        unit2.markTranslated("Texto 2");

        checkpoint.recordCompletedUnit(unit1);
        checkpoint.recordCompletedUnit(unit2);

        assertEquals(2, checkpoint.getCompletedUnits().size());
        assertTrue(checkpoint.getCompletedUnits().contains(unit1));
        assertTrue(checkpoint.getCompletedUnits().contains(unit2));
    }

    @Test
    public void testCheckpointRecordsFailedUnits() {
        Checkpoint checkpoint = new Checkpoint("job-1", 1, null, "ollama");

        TranslationUnit unit1 = new TranslationUnit(1, "Text 1", UnitType.PARAGRAPH, "Spanish");
        unit1.markFailed("Error al traducir");
        unit1.markForRetry();

        checkpoint.recordFailedUnit(unit1);

        assertEquals(1, checkpoint.getFailedUnits().size());
        assertTrue(checkpoint.getFailedUnits().contains(unit1));
    }

    @Test
    public void testCheckpointDoesNotRecordPendingUnits() {
        Checkpoint checkpoint = new Checkpoint("job-1", 1, null, "ollama");

        TranslationUnit pendingUnit = new TranslationUnit(1, "Text", UnitType.PARAGRAPH, "Spanish");
        checkpoint.recordCompletedUnit(pendingUnit);

        // Unidades pending no deberían ser registradas como completed
        assertEquals(0, checkpoint.getCompletedUnits().size());
    }

    @Test
    public void testCheckpointDescriptionIncluesUnitCounts() {
        Checkpoint checkpoint = new Checkpoint("job-1", 1, null, "ollama");

        TranslationUnit unit = new TranslationUnit(1, "Text", UnitType.PARAGRAPH, "Spanish");
        unit.markTranslated("Texto");
        checkpoint.recordCompletedUnit(unit);

        String description = checkpoint.getDescription();
        assertTrue(description.contains("completed={1}"));
    }

    @Test
    public void testCheckpointMaintainsUnitState() {
        Checkpoint checkpoint = new Checkpoint("job-1", 1, null, "ollama");

        TranslationUnit unit = new TranslationUnit(1, "Original", UnitType.PARAGRAPH, "Spanish");
        unit.markTranslated("Traducido");
        unit.putMetadata("source", "pdf");

        checkpoint.recordCompletedUnit(unit);

        TranslationUnit recordedUnit = checkpoint.getCompletedUnits().get(0);
        assertEquals("Traducido", recordedUnit.getTranslatedText());
        assertEquals("pdf", recordedUnit.getMetadata("source", String.class));
    }

    @Test
    public void testCheckpointCanTrackMultipleFailedUnits() {
        Checkpoint checkpoint = new Checkpoint("job-1", 1, null, "ollama");

        TranslationUnit unit1 = new TranslationUnit(1, "Text 1", UnitType.PARAGRAPH, "Spanish");
        TranslationUnit unit2 = new TranslationUnit(1, "Text 2", UnitType.MAP_LABEL, "Spanish");

        unit1.markFailed("Error 1");
        unit1.markForRetry();
        unit2.markFailed("Error 2");
        unit2.markForRetry();

        checkpoint.recordFailedUnit(unit1);
        checkpoint.recordFailedUnit(unit2);

        assertEquals(2, checkpoint.getFailedUnits().size());
        assertEquals(UnitType.PARAGRAPH, checkpoint.getFailedUnits().get(0).getUnitType());
        assertEquals(UnitType.MAP_LABEL, checkpoint.getFailedUnits().get(1).getUnitType());
    }

    @Test
    public void testCheckpointPreservesPageAndJobInfo() {
        Checkpoint checkpoint = new Checkpoint("job-abc", 42, "unit-xyz", "mock-provider");

        assertEquals("job-abc", checkpoint.getJobId());
        assertEquals(42, checkpoint.getPageNumber());
        assertEquals("unit-xyz", checkpoint.getLastCompletedUnitId());
        assertEquals("mock-provider", checkpoint.getProviderModel());
    }
}

