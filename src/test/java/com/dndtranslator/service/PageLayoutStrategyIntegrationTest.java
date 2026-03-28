package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PageLayoutStrategyIntegrationTest {

    private final PageLayoutStrategyFactory factory = new PageLayoutStrategyFactory(new PageLayoutBuilder());

    @Test
    void textHeavyUsesTextHeavyLayoutStrategyAndBuildsBoxes() {
        PageRenderContext context = new PageRenderContext(
                2,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                List.of(),
                List.of(new Paragraph("Long paragraph text", 2, 30f, 720f, "Font", 12f)),
                PageType.TEXT_HEAVY,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.TEXT_HEAVY);
        strategy.renderPage(context);

        assertNotNull(context.getPageLayout());
        assertFalse(context.getPageLayout().textBoxes().isEmpty());
    }

    @Test
    void mapPageUsesMapLayoutStrategy() {
        PdfImagePlacement image = new PdfImagePlacement(
                1,
                new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB),
                80f,
                120f,
                440f,
                520f,
                true,
                "map",
                "exact"
        );

        PageRenderContext context = new PageRenderContext(
                1,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                List.of(image),
                List.of(),
                PageType.MAP_PAGE,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.MAP_PAGE);
        strategy.renderPage(context);

        assertNotNull(context.getPageLayout());
        assertFalse(context.getPageLayout().textBoxes().isEmpty());
    }

    @Test
    void tableOrIndexUsesSingleStructuredBox() {
        PageRenderContext context = new PageRenderContext(
                3,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                List.of(),
                List.of(),
                PageType.TABLE_OR_INDEX,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.TABLE_OR_INDEX);
        strategy.renderPage(context);

        assertNotNull(context.getPageLayout());
        assertEquals(1, context.getPageLayout().textBoxes().size());
    }

    @Test
    void imageHeavyUsesConservativeBoxSelection() {
        PdfImagePlacement mainImage = new PdfImagePlacement(
                4,
                new BufferedImage(260, 260, BufferedImage.TYPE_INT_RGB),
                40f,
                120f,
                500f,
                560f,
                true,
                "full",
                "exact"
        );

        PageRenderContext context = new PageRenderContext(
                4,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                List.of(mainImage),
                List.of(new Paragraph("Caption", 4, 32f, 90f, "Font", 11f)),
                PageType.IMAGE_HEAVY,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.IMAGE_HEAVY);
        strategy.renderPage(context);

        assertNotNull(context.getPageLayout());
        assertFalse(context.getPageLayout().textBoxes().isEmpty());
        assertTrue(context.getPageLayout().textBoxes().size() <= 2);
    }

    @Test
    void titleOrCoverUsesSingleMinimalBox() {
        PageRenderContext context = new PageRenderContext(
                1,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                List.of(),
                List.of(new Paragraph("Title", 1, 40f, 700f, "Font", 18f)),
                PageType.TITLE_OR_COVER,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.TITLE_OR_COVER);
        strategy.renderPage(context);

        assertNotNull(context.getPageLayout());
        assertEquals(1, context.getPageLayout().textBoxes().size());
    }

    @Test
    void unknownUsesStableFallbackLayout() {
        PageRenderContext context = new PageRenderContext(
                5,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                List.of(),
                List.of(),
                PageType.UNKNOWN,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.UNKNOWN);
        strategy.renderPage(context);

        assertNotNull(context.getPageLayout());
        assertFalse(context.getPageLayout().textBoxes().isEmpty());
    }
}
