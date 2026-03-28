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
    void mapPageWithoutImagesUsesExplicitVerticalMargin() {
        PageRenderContext context = new PageRenderContext(
                1,
                new PageMeta(600f, 800f, 80f, 20f, 1, "Font", 12f),
                List.of(),
                List.of(),
                PageType.MAP_PAGE,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.MAP_PAGE);
        strategy.renderPage(context);

        LayoutBox box = context.getPageLayout().textBoxes().get(0);
        assertEquals(80f, box.x());
        assertEquals(24f, box.y());
    }

    @Test
    void mapPageWithLargeTopMarginUsesSameBottomMarginInMapAwareLayout() {
        PdfImagePlacement image = new PdfImagePlacement(
                1,
                new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB),
                120f,
                200f,
                320f,
                360f,
                true,
                "map",
                "exact"
        );

        PageRenderContext context = new PageRenderContext(
                1,
                new PageMeta(600f, 800f, 24f, 60f, 1, "Font", 12f),
                List.of(image),
                List.of(),
                PageType.MAP_PAGE,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.MAP_PAGE);
        strategy.renderPage(context);

        float minY = context.getPageLayout().textBoxes().stream().map(LayoutBox::y).min(Float::compare).orElse(-1f);
        assertEquals(60f, minY);
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
    void tableOrIndexUsesTopMarginForVerticalBounds() {
        PageRenderContext context = new PageRenderContext(
                3,
                new PageMeta(600f, 800f, 80f, 20f, 1, "Font", 12f),
                List.of(),
                List.of(),
                PageType.TABLE_OR_INDEX,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.TABLE_OR_INDEX);
        strategy.renderPage(context);

        LayoutBox box = context.getPageLayout().textBoxes().get(0);
        assertEquals(80f, box.x());
        assertEquals(28f, box.y());
        assertEquals(600f - 80f - 80f, box.width());
        assertEquals(800f - 28f - 28f, box.height());
    }

    @Test
    void tableOrIndexWithDominantVisualBuildsBoxesAroundVisual() {
        PdfImagePlacement dominantVisual = new PdfImagePlacement(
                3,
                new BufferedImage(300, 400, BufferedImage.TYPE_INT_RGB),
                150f,
                180f,
                300f,
                400f,
                true,
                "index-art",
                "exact"
        );

        PageRenderContext context = new PageRenderContext(
                3,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                List.of(dominantVisual),
                List.of(),
                PageType.TABLE_OR_INDEX,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.TABLE_OR_INDEX);
        strategy.renderPage(context);

        assertNotNull(context.getPageLayout());
        assertTrue(context.getPageLayout().textBoxes().size() >= 2);
    }

    @Test
    void tableOrIndexWithSmallDecorativeVisualKeepsSingleFlowBox() {
        PdfImagePlacement decorativeVisual = new PdfImagePlacement(
                3,
                new BufferedImage(80, 80, BufferedImage.TYPE_INT_RGB),
                40f,
                680f,
                80f,
                80f,
                true,
                "badge",
                "exact"
        );

        PageRenderContext context = new PageRenderContext(
                3,
                new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                List.of(decorativeVisual),
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
    void titleOrCoverDoesNotReuseLeftMarginAsBottomMargin() {
        PageRenderContext context = new PageRenderContext(
                1,
                new PageMeta(600f, 800f, 80f, 20f, 1, "Font", 12f),
                List.of(),
                List.of(new Paragraph("Title", 1, 40f, 700f, "Font", 18f)),
                PageType.TITLE_OR_COVER,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.TITLE_OR_COVER);
        strategy.renderPage(context);

        LayoutBox box = context.getPageLayout().textBoxes().get(0);
        assertEquals(80f, box.x());
        assertEquals(36f, box.y());
    }

    @Test
    void titleOrCoverUsesTopMarginWhenItIsGreaterThanMinimum() {
        PageRenderContext context = new PageRenderContext(
                1,
                new PageMeta(600f, 800f, 36f, 70f, 1, "Font", 12f),
                List.of(),
                List.of(new Paragraph("Title", 1, 40f, 700f, "Font", 18f)),
                PageType.TITLE_OR_COVER,
                null
        );

        PageLayoutStrategy strategy = factory.getStrategy(PageType.TITLE_OR_COVER);
        strategy.renderPage(context);

        LayoutBox box = context.getPageLayout().textBoxes().get(0);
        assertEquals(70f, box.y());
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
