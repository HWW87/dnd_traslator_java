package com.dndtranslator.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfImageExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    void detectsEmbeddedImageWithExactBounds() throws Exception {
        Path pdfPath = tempDir.resolve("with-image.pdf");
        createPdfWithImage(pdfPath, 50f, 100f, 120f, 60f);

        PdfImageExtractor extractor = new PdfImageExtractor();
        Map<Integer, List<PdfImagePlacement>> placements = extractor.extract(pdfPath.toString());

        assertTrue(placements.containsKey(1));
        assertEquals(1, placements.get(1).size());

        PdfImagePlacement placement = placements.get(1).getFirst();
        assertTrue(placement.exactBounds());
        assertNotNull(placement.image());
        assertEquals(50f, placement.x(), 2.0f);
        assertEquals(100f, placement.y(), 2.0f);
        assertEquals(120f, placement.width(), 2.0f);
        assertEquals(60f, placement.height(), 2.0f);
    }

    @Test
    void returnsEmptyPageListWhenPageHasNoImages() throws Exception {
        Path pdfPath = tempDir.resolve("without-image.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.LETTER));
            document.save(pdfPath.toFile());
        }

        PdfImageExtractor extractor = new PdfImageExtractor();
        Map<Integer, List<PdfImagePlacement>> placements = extractor.extract(pdfPath.toString());

        assertTrue(placements.containsKey(1));
        assertTrue(placements.get(1).isEmpty());
    }

    @Test
    void fallbackPlacementRemainsRenderableWhenExtractorCannotResolveExactBounds() {
        BufferedImage image = sampleImage();
        PdfImagePlacement placement = new PdfImagePlacement(1, image, 24f, 24f, 100f, 50f, false, "fallback", "fallback-top-right-stack");

        assertFalse(placement.exactBounds());
        assertTrue(placement.isRenderable());
    }

    private void createPdfWithImage(Path pdfPath, float x, float y, float width, float height) throws Exception {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(300f, 400f));
            document.addPage(page);

            PDImageXObject image = LosslessFactory.createFromImage(document, sampleImage());
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.drawImage(image, x, y, width, height);
            }

            document.save(pdfPath.toFile());
        }
    }

    private BufferedImage sampleImage() {
        BufferedImage image = new BufferedImage(80, 40, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, Color.RED.getRGB());
            }
        }
        return image;
    }
}

