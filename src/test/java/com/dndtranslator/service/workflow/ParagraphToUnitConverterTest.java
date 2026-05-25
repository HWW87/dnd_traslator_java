package com.dndtranslator.service.workflow;

import com.dndtranslator.domain.TranslationUnit;
import com.dndtranslator.domain.UnitType;
import com.dndtranslator.model.Paragraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ParagraphToUnitConverterTest {

    private final ParagraphToUnitConverter converter = new ParagraphToUnitConverter();

    @Test
    void convertsParagraphsToUnitsKeepingPageAndLanguage() {
        Paragraph p1 = new Paragraph("Armor Class 15", 2, 100, 120, "Font", 10);
        Paragraph p2 = new Paragraph("Copyright 2026", 3, 110, 140, "Font", 10);

        List<TranslationUnit> units = converter.convert(List.of(p1, p2), "Spanish");

        assertEquals(2, units.size());
        assertEquals(2, units.get(0).getPageNumber());
        assertEquals("Spanish", units.get(0).getTargetLanguage());
        assertEquals(UnitType.SHORT_LABEL, units.get(0).getUnitType());
        assertEquals(UnitType.LEGAL_TEXT, units.get(1).getUnitType());
    }

    @Test
    void infersIndexLineForLeaderDotsPattern() {
        Paragraph paragraph = new Paragraph("Chapter One........12", 1, 10, 20, "Font", 10);

        TranslationUnit unit = converter.toUnit(paragraph, "Spanish");

        assertEquals(UnitType.INDEX_LINE, unit.getUnitType());
    }

    @Test
    void storesParagraphMetadataInUnit() {
        Paragraph paragraph = new Paragraph("Hello", 1, 33, 44, "BookFont", 9);

        TranslationUnit unit = converter.toUnit(paragraph, "Spanish");

        assertNotNull(unit.getMetadata("paragraph_x", Float.class));
        assertNotNull(unit.getMetadata("paragraph_y", Float.class));
        assertEquals("BookFont", unit.getMetadata("paragraph_font", String.class));
        assertEquals(9.0f, unit.getMetadata("paragraph_font_size", Float.class));
    }

    @Test
    void normalizesNoisySourceTextBeforeCreatingUnit() {
        Paragraph paragraph = new Paragraph("Linea\u0007  1\r\n\r\n\r\nLinea\uFFFD 2", 1, 10, 20, "Font", 10);

        TranslationUnit unit = converter.toUnit(paragraph, "Spanish");

        assertEquals("Linea 1\n\nLinea 2", unit.getSourceText());
    }
}

