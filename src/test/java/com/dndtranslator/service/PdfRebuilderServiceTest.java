package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PdfRebuilderServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void rebuildPreservesImageCountForSimplePage() throws Exception {
        Path originalPdf = tempDir.resolve("source.pdf");
        createPdfWithImage(originalPdf, 40f, 220f, 140f, 70f);

        Paragraph paragraph = new Paragraph("Original text", 1, 40f, 160f, "Font", 12f);
        paragraph.setTranslatedText("Texto traducido");

        PdfRebuilderService rebuilder = new PdfRebuilderService();
        rebuilder.rebuild(
                originalPdf.toString(),
                List.of(paragraph),
                Map.of(1, new PageMeta(300f, 400f, 24f, 24f, 1, "Font", 12f))
        );

        Path outputPdf = translatedOutputPath(originalPdf);
        assertTrue(Files.exists(outputPdf));

        PdfImageExtractor extractor = new PdfImageExtractor();
        Map<Integer, List<PdfImagePlacement>> images = extractor.extract(outputPdf.toString());

        assertEquals(1, images.get(1).size());
    }

    @Test
    void rebuildContinuesWhenImageExtractionFails() throws Exception {
        Path originalPdf = tempDir.resolve("source-failing-images.pdf");
        createPdfWithImage(originalPdf, 40f, 220f, 140f, 70f);

        Paragraph paragraph = new Paragraph("Original text", 1, 40f, 160f, "Font", 12f);
        paragraph.setTranslatedText("Texto traducido");

        PdfImageExtractor failingExtractor = new PdfImageExtractor() {
            @Override
            public Map<Integer, List<PdfImagePlacement>> extract(String pdfPath) throws IOException {
                throw new IOException("forced image extraction failure");
            }
        };

        PdfRebuilderService rebuilder = new PdfRebuilderService(failingExtractor);
        rebuilder.rebuild(
                originalPdf.toString(),
                List.of(paragraph),
                Map.of(1, new PageMeta(300f, 400f, 24f, 24f, 1, "Font", 12f))
        );

        Path outputPdf = translatedOutputPath(originalPdf);
        assertTrue(Files.exists(outputPdf));

        PdfImageExtractor extractor = new PdfImageExtractor();
        Map<Integer, List<PdfImagePlacement>> images = extractor.extract(outputPdf.toString());
        assertTrue(images.get(1).isEmpty());
    }

    @Test
    void movesTextBelowImageWhenParagraphWouldOverlap() throws Exception {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            PDType0Font font = PDType0Font.load(document, fontStream);
            PdfRebuilderService rebuilder = new PdfRebuilderService();

            PdfImagePlacement imagePlacement = new PdfImagePlacement(
                    1,
                    sampleImage(),
                    40f,
                    180f,
                    140f,
                    70f,
                    true,
                    "test-image",
                    "exact-bounding-box"
            );

            float safeY = rebuilder.resolveSafeY(
                    font,
                    "Texto traducido que pisaria la imagen",
                    40f,
                    210f,
                    12f,
                    140f,
                    new PageMeta(300f, 400f, 24f, 24f, 1, "Font", 12f),
                    List.of(imagePlacement)
            );

            assertTrue(safeY < 180f);
        }
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
        BufferedImage image = new BufferedImage(100, 50, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, Color.BLUE.getRGB());
            }
        }
        return image;
    }

    private Path translatedOutputPath(Path originalPdf) {
        String original = originalPdf.toString();
        return Path.of(original.replace(".pdf", "_translated_layout.pdf"));
    }
}

