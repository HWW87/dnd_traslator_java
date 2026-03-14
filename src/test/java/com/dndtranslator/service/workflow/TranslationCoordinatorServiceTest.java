package com.dndtranslator.service.workflow;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationCoordinatorServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void executesWithEmbeddedTextWithoutOcrFallback() throws Exception {
        File pdf = createDummyPdf();

        List<Paragraph> embeddedParagraphs = new ArrayList<>();
        embeddedParagraphs.add(new Paragraph("Hello world", 1, 100, 100, "Font", 10));
        embeddedParagraphs.add(new Paragraph("Another line", 1, 100, 120, "Font", 10));

        Map<Integer, PageMeta> embeddedLayout = new HashMap<>();
        embeddedLayout.put(1, new PageMeta(595, 842, 50, 50, 1, "Font", 10));

        CapturingRebuilder rebuilder = new CapturingRebuilder();
        CapturingListener listener = new CapturingListener();

        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                new ForcedQualityEvaluator(false),
                new TextSanitizer(),
                (text, lang) -> "ES:" + text,
                rebuilder,
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, embeddedLayout),
                file -> new TranslationCoordinatorService.ExtractionSnapshot(List.of(), Map.of()),
                () -> {
                }
        );

        TranslationResult result = coordinator.execute(new TranslationRequest(pdf, "Spanish"), listener);

        assertFalse(result.usedOcrFallback());
        assertEquals(2, result.paragraphCount());
        assertTrue(result.outputPdfPath().endsWith("_translated_layout.pdf"));

        assertEquals("ES:Hello world", embeddedParagraphs.get(0).getTranslatedText());
        assertEquals("ES:Another line", embeddedParagraphs.get(1).getTranslatedText());

        assertEquals(pdf.getAbsolutePath(), rebuilder.originalPath);
        assertEquals(2, rebuilder.paragraphs.size());
        assertEquals(2, listener.progressEvents);
        assertTrue(listener.logs.stream().anyMatch(msg -> msg.contains("detectados: 2")));
    }

    @Test
    void executesWithOcrFallbackWhenQualityIsPoor() throws Exception {
        File pdf = createDummyPdf();

        List<Paragraph> embeddedParagraphs = List.of(new Paragraph("bad", 1, 10, 10, "Font", 10));
        List<Paragraph> ocrParagraphs = new ArrayList<>();
        ocrParagraphs.add(new Paragraph("Scanned line", 1, 200, 200, "OCR", 11));

        Map<Integer, PageMeta> layout = Map.of(1, new PageMeta(595, 842, 50, 50, 1, "Font", 10));

        CapturingRebuilder rebuilder = new CapturingRebuilder();
        CapturingListener listener = new CapturingListener();

        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                new ForcedQualityEvaluator(true),
                new TextSanitizer(),
                (text, lang) -> "TR:" + text,
                rebuilder,
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, layout),
                file -> new TranslationCoordinatorService.ExtractionSnapshot(ocrParagraphs, layout),
                () -> {
                }
        );

        TranslationResult result = coordinator.execute(new TranslationRequest(pdf, "Spanish"), listener);

        assertTrue(result.usedOcrFallback());
        assertEquals(1, result.paragraphCount());
        assertEquals("TR:Scanned line", ocrParagraphs.get(0).getTranslatedText());
        assertTrue(listener.logs.stream().anyMatch(msg -> msg.contains("Activando OCR embebido")));
        assertEquals(1, listener.progressEvents);
    }

    private File createDummyPdf() throws Exception {
        Path pdfPath = tempDir.resolve("sample.pdf");
        Files.writeString(pdfPath, "dummy");
        return pdfPath.toFile();
    }

    private static class ForcedQualityEvaluator extends ExtractionQualityEvaluator {

        private final boolean forceFallback;

        private ForcedQualityEvaluator(boolean forceFallback) {
            this.forceFallback = forceFallback;
        }

        @Override
        public boolean shouldUseOcrFallback(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) {
            return forceFallback;
        }
    }

    private static class CapturingRebuilder implements TranslationCoordinatorService.PdfRebuilderGateway {
        private String originalPath;
        private List<Paragraph> paragraphs = List.of();

        @Override
        public void rebuild(String originalPath, List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) {
            this.originalPath = originalPath;
            this.paragraphs = new ArrayList<>(paragraphs);
        }
    }

    private static class CapturingListener implements TranslationProgressListener {
        private final List<String> logs = new ArrayList<>();
        private int progressEvents;

        @Override
        public void onLog(String message) {
            logs.add(message);
        }

        @Override
        public void onProgress(int completed, int total) {
            progressEvents++;
        }
    }
}

