package com.dndtranslator.service;

import com.dndtranslator.domain.TranslationUnit;
import com.dndtranslator.domain.UnitState;
import com.dndtranslator.domain.UnitType;
import com.dndtranslator.infrastructure.MockTranslationProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests para TranslatorService.translateUnit() - Phase 10 Canonical Path
 * Valida que TranslationUnit sea la unidad atómica canónica de traducción.
 */
public class TranslatorServiceUnitCanonicalTest {

    private TranslatorService createService() {
        MockTranslationProvider provider = new MockTranslationProvider();
        TranslationCacheRepository cache = new TranslationCacheRepository();
        TranslationSegmenter segmenter = new TranslationSegmenter();
        ModelResolver modelResolver = new ModelResolver();

        return new TranslatorService(
                provider,
                cache,
                segmenter,
                modelResolver
        );
    }

    @Test
    public void testTranslateUnitSuccessfully() {
        TranslatorService service = createService();
        TranslationUnit unit = new TranslationUnit(
                1,
                "Hello world",
                UnitType.PARAGRAPH,
                "Spanish"
        );

        TranslationUnit result = service.translateUnit(unit);

        assertNotNull(result);
        assertEquals(unit.getId(), result.getId());
        assertTrue(result.isTranslated());
        assertEquals(UnitState.TRANSLATED, result.getState());
        assertNotNull(result.getTranslatedText());
        assertFalse(result.getTranslatedText().isBlank());
    }

    @Test
    public void testTranslateUnitWithEmptyText() {
        TranslatorService service = createService();
        TranslationUnit unit = new TranslationUnit(
                1,
                "",
                UnitType.PARAGRAPH,
                "Spanish"
        );

        TranslationUnit result = service.translateUnit(unit);

        assertNotNull(result);
        assertTrue(result.isSkipped());
        assertEquals(UnitState.SKIPPED, result.getState());
    }

    @Test
    public void testTranslateUnitNullUnit() {
        TranslatorService service = createService();
        TranslationUnit result = service.translateUnit(null);
        assertNull(result);
    }

    @Test
    public void testTranslateUnitPreservesMetadata() {
        TranslatorService service = createService();
        TranslationUnit unit = new TranslationUnit(
                2,
                "Test paragraph",
                UnitType.PARAGRAPH,
                "Spanish"
        );
        unit.putMetadata("source", "pdf");

        TranslationUnit result = service.translateUnit(unit);

        assertEquals("pdf", result.getMetadata("source", String.class));
    }

    @Test
    public void testTranslateUnitWithIndexType() {
        TranslatorService service = createService();
        TranslationUnit unit = new TranslationUnit(
                3,
                "Chapter 1 ......................... 25",
                UnitType.INDEX_LINE,
                "Spanish"
        );

        TranslationUnit result = service.translateUnit(unit);

        assertNotNull(result);
        assertTrue(result.isTranslated() || result.getState() == UnitState.TRANSLATED);
    }

    @Test
    public void testTranslateUnitWithMapLabelType() {
        TranslatorService service = createService();
        TranslationUnit unit = new TranslationUnit(
                4,
                "North sector",
                UnitType.MAP_LABEL,
                "Spanish"
        );

        TranslationUnit result = service.translateUnit(unit);

        assertNotNull(result);
        assertTrue(result.isTranslated() || result.getState() == UnitState.TRANSLATED);
        assertEquals(0, result.getRetryCount());
    }

    @Test
    public void testTranslateUnitStateTransition() {
        TranslatorService service = createService();
        TranslationUnit unit = new TranslationUnit(
                5,
                "Sample text",
                UnitType.PARAGRAPH,
                "Spanish"
        );

        assertTrue(unit.isPending());
        TranslationUnit result = service.translateUnit(unit);
        assertTrue(result.isTranslated());
    }

    @Test
    public void testTranslateMultipleUnitsIndependently() {
        TranslatorService service = createService();

        TranslationUnit unit1 = new TranslationUnit(
                1,
                "First unit",
                UnitType.PARAGRAPH,
                "Spanish"
        );

        TranslationUnit unit2 = new TranslationUnit(
                1,
                "Second unit",
                UnitType.PARAGRAPH,
                "Spanish"
        );

        TranslationUnit result1 = service.translateUnit(unit1);
        TranslationUnit result2 = service.translateUnit(unit2);

        assertTrue(result1.isTranslated());
        assertTrue(result2.isTranslated());

        // Units should have different IDs
        assertNotEquals(result1.getId(), result2.getId());

        // Both should have translations
        assertNotNull(result1.getTranslatedText());
        assertNotNull(result2.getTranslatedText());
    }

    @Test
    public void testTranslateUnitMaintainsUnitType() {
        TranslatorService service = createService();
        TranslationUnit unit = new TranslationUnit(
                1,
                "Legal text",
                UnitType.LEGAL_TEXT,
                "Spanish"
        );

        TranslationUnit result = service.translateUnit(unit);

        assertEquals(UnitType.LEGAL_TEXT, result.getUnitType());
    }

    @Test
    public void testTranslateUnitMaintainsPageNumber() {
        TranslatorService service = createService();
        TranslationUnit unit = new TranslationUnit(
                42,
                "Text on page 42",
                UnitType.PARAGRAPH,
                "Spanish"
        );

        TranslationUnit result = service.translateUnit(unit);

        assertEquals(42, result.getPageNumber());
    }

    @Test
    public void testTranslateUnitMaintainsTargetLanguage() {
        TranslatorService service = createService();
        TranslationUnit unit = new TranslationUnit(
                1,
                "English text",
                UnitType.PARAGRAPH,
                "Portuguese"
        );

        TranslationUnit result = service.translateUnit(unit);

        assertEquals("Portuguese", result.getTargetLanguage());
    }
}

