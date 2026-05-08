package com.dndtranslator.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para TranslationUnit (Phase 1).
 */
public class TranslationUnitTest {

    @Test
    public void testTranslationUnitCreation() {
        TranslationUnit unit = new TranslationUnit(
                1,
                "Hola mundo",
                UnitType.PARAGRAPH,
                "English"
        );

        assertNotNull(unit.getId());
        assertEquals(1, unit.getPageNumber());
        assertEquals("Hola mundo", unit.getSourceText());
        assertEquals(UnitType.PARAGRAPH, unit.getUnitType());
        assertEquals("English", unit.getTargetLanguage());
        assertEquals(UnitState.PENDING, unit.getState());
        assertNull(unit.getTranslatedText());
        assertEquals(0, unit.getRetryCount());
    }

    @Test
    public void testMarkTranslated() {
        TranslationUnit unit = new TranslationUnit(
                1,
                "Hola mundo",
                UnitType.PARAGRAPH,
                "English"
        );

        unit.markTranslated("Hello world");

        assertEquals(UnitState.TRANSLATED, unit.getState());
        assertEquals("Hello world", unit.getTranslatedText());
        assertNull(unit.getLastError());
        assertTrue(unit.isTranslated());
    }

    @Test
    public void testMarkFailed() {
        TranslationUnit unit = new TranslationUnit(
                1,
                "Test",
                UnitType.PARAGRAPH,
                "English"
        );

        unit.markFailed("Connection timeout");

        assertEquals(UnitState.FAILED, unit.getState());
        assertEquals("Connection timeout", unit.getLastError());
        assertEquals(1, unit.getRetryCount());
        assertTrue(unit.needsRetry());
    }

    @Test
    public void testMarkForRetry() {
        TranslationUnit unit = new TranslationUnit(
                1,
                "Test",
                UnitType.PARAGRAPH,
                "English"
        );

        unit.markFailed("Error");
        unit.markForRetry();

        assertEquals(UnitState.RETRY_NEEDED, unit.getState());
        assertTrue(unit.needsRetry());
    }

    @Test
    public void testMarkSkipped() {
        TranslationUnit unit = new TranslationUnit(
                1,
                "Test",
                UnitType.PARAGRAPH,
                "English"
        );

        unit.markSkipped("Too short");

        assertEquals(UnitState.SKIPPED, unit.getState());
        assertTrue(unit.isComplete());
        assertTrue(unit.isSkipped());
    }

    @Test
    public void testMetadata() {
        TranslationUnit unit = new TranslationUnit(
                1,
                "Test",
                UnitType.PARAGRAPH,
                "English"
        );

        unit.putMetadata("font_size", 12);
        unit.putMetadata("color", "red");

        assertEquals(12, unit.getMetadata("font_size", Integer.class));
        assertEquals("red", unit.getMetadata("color", String.class));
    }

    @Test
    public void testStatus() {
        TranslationUnit unit = new TranslationUnit(
                1,
                "Test",
                UnitType.PARAGRAPH,
                "English"
        );

        assertTrue(unit.isPending());
        assertFalse(unit.isComplete());

        unit.markTranslated("Prueba");
        assertFalse(unit.isPending());
        assertTrue(unit.isComplete());
    }
}

