package com.dndtranslator.service.workflow;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationCoordinatorServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void rejectsNullRequest() {
        TranslationCoordinatorService coordinator = buildCoordinator(
                false,
                List.of(paragraph("hello world")),
                List.of(),
                (text, lang) -> text,
                new CapturingRebuilder(),
                new GlossaryService(List.of())
        );

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.execute(null, new CapturingListener())
        );

        assertTrue(error.getMessage().contains("invalida"));
    }

    @Test
    void rejectsUnreadableRequestFile() {
        TranslationCoordinatorService coordinator = buildCoordinator(
                false,
                List.of(paragraph("hello world")),
                List.of(),
                (text, lang) -> text,
                new CapturingRebuilder(),
                new GlossaryService(List.of())
        );

        File missing = tempDir.resolve("missing.pdf").toFile();

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> coordinator.execute(new TranslationRequest(missing, "Spanish"), new CapturingListener())
        );

        assertTrue(error.getMessage().contains("no existe"));
    }

    @Test
    void executesWithoutOcrFallbackAndAppliesGlossary() throws Exception {
        File pdf = createDummyPdf();
        List<Paragraph> embeddedParagraphs = new ArrayList<>();
        embeddedParagraphs.add(paragraph("Armor Class 15"));
        embeddedParagraphs.add(paragraph("Hit Points 9"));

        CapturingRebuilder rebuilder = new CapturingRebuilder();
        CapturingListener listener = new CapturingListener();
        AtomicInteger translatorCalls = new AtomicInteger();
        AtomicInteger ocrCalls = new AtomicInteger();

        GlossaryService glossary = new GlossaryService(List.of(
                new GlossaryEntry("Armor Class", "Clase de Armadura", false),
                new GlossaryEntry("Hit Points", "Puntos de Golpe", false)
        ));

        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                (paragraphs, layout) -> false,
                new TextSanitizer(),
                glossary,
                new ParagraphTranslationExecutor(1),
                (text, lang) -> {
                    translatorCalls.incrementAndGet();
                    return "ES:" + text;
                },
                rebuilder,
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, onePageLayout()),
                file -> {
                    ocrCalls.incrementAndGet();
                    return new TranslationCoordinatorService.ExtractionSnapshot(List.of(), onePageLayout());
                },
                () -> {
                }
        );

        TranslationResult result = coordinator.execute(new TranslationRequest(pdf, "Spanish"), listener);

        assertFalse(result.usedOcrFallback());
        assertEquals(2, result.paragraphCount());
        assertEquals(2, translatorCalls.get());
        assertEquals(0, ocrCalls.get());
        assertEquals("ES:Clase de Armadura 15", embeddedParagraphs.get(0).getTranslatedText());
        assertEquals("ES:Puntos de Golpe 9", embeddedParagraphs.get(1).getTranslatedText());
        assertEquals(2, listener.progressEvents);
        assertNotNull(rebuilder.originalPath);
        assertEquals(2, rebuilder.paragraphs.size());
    }

    @Test
    void executesWithOcrFallbackWhenRequested() throws Exception {
        File pdf = createDummyPdf();
        List<Paragraph> embeddedParagraphs = List.of(paragraph("bad"));
        List<Paragraph> ocrParagraphs = new ArrayList<>();
        ocrParagraphs.add(paragraph("Scanned line"));

        CapturingRebuilder rebuilder = new CapturingRebuilder();
        CapturingListener listener = new CapturingListener();

        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                (paragraphs, layout) -> true,
                new TextSanitizer(),
                new GlossaryService(List.of()),
                new ParagraphTranslationExecutor(1),
                (text, lang) -> "TR:" + text,
                rebuilder,
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, onePageLayout()),
                file -> new TranslationCoordinatorService.ExtractionSnapshot(ocrParagraphs, onePageLayout()),
                () -> {
                }
        );

        TranslationResult result = coordinator.execute(new TranslationRequest(pdf, "Spanish"), listener);

        assertTrue(result.usedOcrFallback());
        assertEquals("TR:Scanned line", ocrParagraphs.get(0).getTranslatedText());
        assertTrue(listener.logs.stream().anyMatch(msg -> msg.contains("Activando OCR embebido")));
        assertEquals(1, listener.progressEvents);
        assertEquals(1, rebuilder.paragraphs.size());
    }

    @Test
    void supportsCancellation() throws Exception {
        File pdf = createDummyPdf();
        List<Paragraph> embeddedParagraphs = List.of(paragraph("a very long paragraph to translate"));

        CapturingListener listener = new CapturingListener();
        listener.stopped = true;

        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                (paragraphs, layout) -> false,
                new TextSanitizer(),
                new GlossaryService(List.of()),
                new ParagraphTranslationExecutor(1),
                (text, lang) -> "TR:" + text,
                new CapturingRebuilder(),
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, onePageLayout()),
                file -> new TranslationCoordinatorService.ExtractionSnapshot(List.of(), onePageLayout()),
                () -> {
                }
        );

        assertThrows(CancellationException.class,
                () -> coordinator.execute(new TranslationRequest(pdf, "Spanish"), listener));
    }

    @Test
    void surfacesTranslatorErrors() throws Exception {
        File pdf = createDummyPdf();
        List<Paragraph> embeddedParagraphs = List.of(paragraph("some text"));

        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                (paragraphs, layout) -> false,
                new TextSanitizer(),
                new GlossaryService(List.of()),
                new ParagraphTranslationExecutor(1),
                (text, lang) -> {
                    throw new IllegalStateException("translator unavailable");
                },
                new CapturingRebuilder(),
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, onePageLayout()),
                file -> new TranslationCoordinatorService.ExtractionSnapshot(List.of(), onePageLayout()),
                () -> {
                }
        );

        ExecutionException error = assertThrows(ExecutionException.class,
                () -> coordinator.execute(new TranslationRequest(pdf, "Spanish"), new CapturingListener()));

        assertTrue(error.getCause() instanceof IllegalStateException);
    }

    private TranslationCoordinatorService buildCoordinator(
            boolean forceOcr,
            List<Paragraph> embedded,
            List<Paragraph> ocr,
            TranslationCoordinatorService.TranslatorGateway translator,
            TranslationCoordinatorService.PdfRebuilderGateway rebuilder,
            GlossaryService glossary
    ) {
        return new TranslationCoordinatorService(
                (paragraphs, layout) -> forceOcr,
                new TextSanitizer(),
                glossary,
                new ParagraphTranslationExecutor(1),
                translator,
                rebuilder,
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embedded, onePageLayout()),
                file -> new TranslationCoordinatorService.ExtractionSnapshot(ocr, onePageLayout()),
                () -> {
                }
        );
    }

    private File createDummyPdf() throws Exception {
        Path pdfPath = tempDir.resolve("sample.pdf");
        Files.writeString(pdfPath, "dummy");
        return pdfPath.toFile();
    }

    private static Paragraph paragraph(String text) {
        return new Paragraph(text, 1, 100, 100, "Font", 10);
    }

    private static Map<Integer, PageMeta> onePageLayout() {
        return Map.of(1, new PageMeta(595, 842, 50, 50, 1, "Font", 10));
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

    private static class CapturingListener implements TranslationEventListener {
        private final List<String> logs = new ArrayList<>();
        private int progressEvents;
        private boolean stopped;

        @Override
        public void onLog(String message) {
            logs.add(message);
        }

        @Override
        public void onProgress(int completed, int total) {
            progressEvents++;
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }
    }
}

