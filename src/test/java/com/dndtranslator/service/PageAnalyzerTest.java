package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageAnalyzerTest {

    private final PageAnalyzer analyzer = new PageAnalyzer();

    @Test
    void analyzesWithDefaultPageSizeWhenMetaIsNull() {
        Paragraph p = new Paragraph("Armor Class 15", 1, 20f, 700f, "Font", 12f);

        PageAnalysisData data = analyzer.analyze(1, null, List.of(), List.of(p));

        assertEquals(612f, data.pageWidth());
        assertEquals(792f, data.pageHeight());
        assertEquals(1, data.textBlockCount());
    }

    @Test
    void detectsMapKeywordsFromOriginalTextAndIgnoresBlankBlocks() {
        Paragraph blank = new Paragraph("   ", 2, 20f, 700f, "Font", 12f);
        Paragraph map1 = new Paragraph("Mapa de zona norte", 2, 20f, 660f, "Font", 12f);
        Paragraph map2 = new Paragraph("Sector cuadrante base", 2, 20f, 620f, "Font", 12f);
        map1.setTranslatedText("translated without map words");
        map2.setTranslatedText("translated without map words");

        PageAnalysisData data = analyzer.analyze(
                2,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                List.of(),
                List.of(blank, map1, map2)
        );

        assertEquals(2, data.textBlockCount());
        assertTrue(data.hasMapLikeKeywords());
    }
}

