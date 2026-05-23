package com.dndtranslator.service.workflow;

import com.dndtranslator.domain.JobState;
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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationCoordinatorResumeTest {

    @TempDir
    Path tempDir;

    @Test
    void resumesFromCheckpointAndSkipsAlreadyTranslatedParagraphs() throws Exception {
        File pdf = createDummyPdf();
        List<Paragraph> embeddedParagraphs = new ArrayList<>();
        embeddedParagraphs.add(paragraph("linea 1"));
        embeddedParagraphs.add(paragraph("linea 2"));

        InMemoryCheckpointStore checkpointStore = new InMemoryCheckpointStore(
                new CheckpointSnapshot(
                        pdf.getAbsolutePath() + "|spanish",
                        pdf.getAbsolutePath(),
                        "Spanish",
                        2,
                        0,
                        false,
                        Map.of(0, "restaurada 1")
                )
        );

        AtomicInteger translatorCalls = new AtomicInteger();
        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                (paragraphs, layout) -> false,
                new TextSanitizer(),
                new GlossaryService(List.of()),
                new ParagraphTranslationExecutor(1),
                (text, lang) -> {
                    translatorCalls.incrementAndGet();
                    return "TR:" + text;
                },
                (originalPath, paragraphs, layoutInfo) -> {
                },
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, onePageLayout()),
                file -> new TranslationCoordinatorService.ExtractionSnapshot(List.of(), onePageLayout()),
                () -> {
                },
                checkpointStore
        );

        TranslationResult result = coordinator.execute(new TranslationRequest(pdf, "Spanish"), new SilentListener());

        assertEquals(2, result.paragraphCount());
        assertEquals(1, translatorCalls.get(), "Solo debe traducir el parrafo pendiente.");
        assertEquals("restaurada 1", embeddedParagraphs.get(0).getTranslatedText());
        assertEquals("TR:linea 2", embeddedParagraphs.get(1).getTranslatedText());
        assertTrue(checkpointStore.cleared, "El checkpoint debe limpiarse tras completar.");
    }

    @Test
    void executeWithJobReturnsCompletedJobState() throws Exception {
        File pdf = createDummyPdf();
        List<Paragraph> embeddedParagraphs = new ArrayList<>();
        embeddedParagraphs.add(paragraph("linea 1"));
        embeddedParagraphs.add(paragraph("linea 2"));

        InMemoryCheckpointStore checkpointStore = new InMemoryCheckpointStore(
                new CheckpointSnapshot(
                        pdf.getAbsolutePath() + "|spanish",
                        pdf.getAbsolutePath(),
                        "Spanish",
                        2,
                        0,
                        false,
                        Map.of(0, "restaurada 1")
                )
        );

        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                (paragraphs, layout) -> false,
                new TextSanitizer(),
                new GlossaryService(List.of()),
                new ParagraphTranslationExecutor(1),
                (text, lang) -> "TR:" + text,
                (originalPath, paragraphs, layoutInfo) -> {
                },
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, onePageLayout()),
                file -> new TranslationCoordinatorService.ExtractionSnapshot(List.of(), onePageLayout()),
                () -> {
                },
                checkpointStore
        );

        TranslationCoordinatorService.TranslationExecutionOutcome outcome =
                coordinator.executeWithJob(new TranslationRequest(pdf, "Spanish"), new SilentListener());

        assertEquals(JobState.COMPLETED, outcome.job().getCurrentState());
        assertEquals(2, outcome.job().getTotalUnits());
        assertEquals(2, outcome.result().paragraphCount());
        assertEquals(2, outcome.job().getMetric("total_paragraphs", Integer.class));
        assertEquals(2, outcome.job().getMetric("translated_paragraphs", Integer.class));
        assertEquals(1, outcome.job().getMetric("resumed_paragraphs", Integer.class));
    }

    @Test
    void resumesUsingUnitIdsWhenParagraphIndexesChange() throws Exception {
        File pdf = createDummyPdf();
        List<Paragraph> embeddedParagraphs = new ArrayList<>();
        embeddedParagraphs.add(paragraph("linea 2"));
        embeddedParagraphs.add(paragraph("linea 1"));

        InMemoryCheckpointStore checkpointStore = new InMemoryCheckpointStore(
                new CheckpointSnapshot(
                        pdf.getAbsolutePath() + "|spanish",
                        pdf.getAbsolutePath(),
                        "Spanish",
                        2,
                        0,
                        false,
                        Map.of(0, "restaurada 1"),
                        Map.of(0, deterministicUnitId(paragraph("linea 1")))
                )
        );

        AtomicInteger translatorCalls = new AtomicInteger();
        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                (paragraphs, layout) -> false,
                new TextSanitizer(),
                new GlossaryService(List.of()),
                new ParagraphTranslationExecutor(1),
                (text, lang) -> {
                    translatorCalls.incrementAndGet();
                    return "TR:" + text;
                },
                (originalPath, paragraphs, layoutInfo) -> {
                },
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, onePageLayout()),
                file -> new TranslationCoordinatorService.ExtractionSnapshot(List.of(), onePageLayout()),
                () -> {
                },
                checkpointStore
        );

        coordinator.execute(new TranslationRequest(pdf, "Spanish"), new SilentListener());

        assertEquals(1, translatorCalls.get(), "Debe traducir solo el parrafo no restaurado por unitId.");
        assertEquals("TR:linea 2", embeddedParagraphs.get(0).getTranslatedText());
        assertEquals("restaurada 1", embeddedParagraphs.get(1).getTranslatedText());
    }

    @Test
    void resumesUsingTranslatedByUnitIdEvenWhenIndexPayloadIsEmpty() throws Exception {
        File pdf = createDummyPdf();
        List<Paragraph> embeddedParagraphs = new ArrayList<>();
        embeddedParagraphs.add(paragraph("linea 2"));
        embeddedParagraphs.add(paragraph("linea 1"));

        String unitIdLinea1 = deterministicUnitId(paragraph("linea 1"));
        InMemoryCheckpointStore checkpointStore = new InMemoryCheckpointStore(
                new CheckpointSnapshot(
                        pdf.getAbsolutePath() + "|spanish",
                        pdf.getAbsolutePath(),
                        "Spanish",
                        2,
                        0,
                        false,
                        Map.of(),
                        Map.of(),
                        Map.of(unitIdLinea1, "restaurada 1")
                )
        );

        AtomicInteger translatorCalls = new AtomicInteger();
        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                (paragraphs, layout) -> false,
                new TextSanitizer(),
                new GlossaryService(List.of()),
                new ParagraphTranslationExecutor(1),
                (text, lang) -> {
                    translatorCalls.incrementAndGet();
                    return "TR:" + text;
                },
                (originalPath, paragraphs, layoutInfo) -> {
                },
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, onePageLayout()),
                file -> new TranslationCoordinatorService.ExtractionSnapshot(List.of(), onePageLayout()),
                () -> {
                },
                checkpointStore
        );

        coordinator.execute(new TranslationRequest(pdf, "Spanish"), new SilentListener());

        assertEquals(1, translatorCalls.get(), "Debe traducir solo el párrafo no restaurado por unitId explícito.");
        assertEquals("TR:linea 2", embeddedParagraphs.get(0).getTranslatedText());
        assertEquals("restaurada 1", embeddedParagraphs.get(1).getTranslatedText());
    }

    @Test
    void resumesFromCurrentUnitBoundaryWhenCheckpointHasCursor() throws Exception {
        File pdf = createDummyPdf();
        List<Paragraph> embeddedParagraphs = new ArrayList<>();
        embeddedParagraphs.add(paragraph("linea 1"));
        embeddedParagraphs.add(paragraph("linea 2"));
        embeddedParagraphs.add(paragraph("linea 3"));

        String currentUnitId = deterministicUnitId(embeddedParagraphs.get(1));
        InMemoryCheckpointStore checkpointStore = new InMemoryCheckpointStore(
                new CheckpointSnapshot(
                        pdf.getAbsolutePath() + "|spanish",
                        pdf.getAbsolutePath(),
                        "Spanish",
                        3,
                        0,
                        false,
                        1,
                        currentUnitId,
                        deterministicUnitId(embeddedParagraphs.get(0)),
                        1,
                        0,
                        0,
                        0,
                        Map.of(),
                        Map.of(),
                        Map.of()
                )
        );

        AtomicInteger translatorCalls = new AtomicInteger();
        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                (paragraphs, layout) -> false,
                new TextSanitizer(),
                new GlossaryService(List.of()),
                new ParagraphTranslationExecutor(1),
                (text, lang) -> {
                    translatorCalls.incrementAndGet();
                    return "TR:" + text;
                },
                (originalPath, paragraphs, layoutInfo) -> {
                },
                path -> new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, onePageLayout()),
                file -> new TranslationCoordinatorService.ExtractionSnapshot(List.of(), onePageLayout()),
                () -> {
                },
                checkpointStore
        );

        coordinator.execute(new TranslationRequest(pdf, "Spanish"), new SilentListener());

        assertEquals(2, translatorCalls.get(), "Debe reanudar desde currentUnitId y traducir desde esa frontera.");
        assertEquals("", embeddedParagraphs.get(0).getTranslatedText());
        assertEquals("TR:linea 2", embeddedParagraphs.get(1).getTranslatedText());
        assertEquals("TR:linea 3", embeddedParagraphs.get(2).getTranslatedText());
    }

    private File createDummyPdf() throws Exception {
        Path pdfPath = tempDir.resolve("resume.pdf");
        Files.writeString(pdfPath, "dummy");
        return pdfPath.toFile();
    }

    private static Paragraph paragraph(String text) {
        return new Paragraph(text, 1, 100, 100, "Font", 10);
    }

    private static String deterministicUnitId(Paragraph paragraph) {
        String text = paragraph.getFullText();
        String normalizedText = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        int textHash = normalizedText.hashCode();
        int roundedX = Math.round(paragraph.getX());
        int roundedY = Math.round(paragraph.getY());
        return "p" + paragraph.getPage() + "-x" + roundedX + "-y" + roundedY + "-h" + Integer.toHexString(textHash);
    }

    private static Map<Integer, PageMeta> onePageLayout() {
        return Map.of(1, new PageMeta(595, 842, 50, 50, 1, "Font", 10));
    }

    private static class SilentListener implements TranslationEventListener {
        @Override
        public void onLog(String message) {
        }
    }

    private static class InMemoryCheckpointStore implements CheckpointStore {
        private CheckpointSnapshot snapshot;
        private boolean cleared;

        private InMemoryCheckpointStore(CheckpointSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public Optional<CheckpointSnapshot> load(String jobKey) {
            if (snapshot == null || !snapshot.jobKey().equals(jobKey)) {
                return Optional.empty();
            }
            return Optional.of(snapshot);
        }

        @Override
        public void save(CheckpointSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public void clear(String jobKey) {
            if (snapshot != null && snapshot.jobKey().equals(jobKey)) {
                snapshot = null;
                cleared = true;
            }
        }
    }
}

