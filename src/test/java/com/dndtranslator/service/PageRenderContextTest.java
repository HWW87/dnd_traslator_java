package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageRenderContextTest {

    @Test
    void storesImmutableCopiesAndKeepsAnalysisData() {
        List<PdfImagePlacement> images = new ArrayList<>();
        images.add(new PdfImagePlacement(
                1,
                new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB),
                10f,
                10f,
                100f,
                80f,
                true,
                "image",
                "exact"
        ));

        List<Paragraph> paragraphs = new ArrayList<>();
        paragraphs.add(new Paragraph("text", 1, 20f, 700f, "Font", 12f));

        PageAnalysisData analysisData = new PageAnalysisData(
                1, 600f, 800f, 1, 0.2f,
                1, 1, 5, 1, 0,
                true, false, false, false, false, false
        );

        PageRenderContext context = new PageRenderContext(
                1,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                images,
                paragraphs,
                PageType.MIXED_LAYOUT,
                analysisData
        );

        images.clear();
        paragraphs.clear();

        assertEquals(1, context.getPageImages().size());
        assertEquals(1, context.getParagraphs().size());
        assertEquals(analysisData, context.getAnalysisData());

        assertThrows(UnsupportedOperationException.class, () -> context.getPageImages().add(null));
        assertThrows(UnsupportedOperationException.class, () -> context.getParagraphs().add(null));
    }

    @Test
    void helperMethodsReflectPageContent() {
        PageRenderContext emptyContext = new PageRenderContext(
                2,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                List.of(),
                List.of(),
                PageType.UNKNOWN,
                null
        );

        assertFalse(emptyContext.hasImages());
        assertFalse(emptyContext.hasParagraphs());
        assertTrue(emptyContext.isEmptyPage());

        PageRenderContext nonEmptyContext = new PageRenderContext(
                2,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                List.of(new PdfImagePlacement(
                        2,
                        new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB),
                        10f,
                        10f,
                        100f,
                        80f,
                        true,
                        "image",
                        "exact"
                )),
                List.of(new Paragraph("text", 2, 20f, 700f, "Font", 12f)),
                PageType.MIXED_LAYOUT,
                null
        );

        assertTrue(nonEmptyContext.hasImages());
        assertTrue(nonEmptyContext.hasParagraphs());
        assertFalse(nonEmptyContext.isEmptyPage());
        assertNotNull(nonEmptyContext.getPageMeta());
    }
}

