package com.dndtranslator.service.workflow;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TranslationCoordinatorServiceMockitoTest {

    @TempDir
    Path tempDir;

    @Mock
    private TranslationCoordinatorService.TranslatorGateway translatorGateway;

    @Mock
    private TranslationCoordinatorService.PdfRebuilderGateway pdfRebuilderGateway;

    @Mock
    private TranslationCoordinatorService.EmbeddedExtractor embeddedExtractor;

    @Mock
    private TranslationCoordinatorService.OcrExtractor ocrExtractor;

    @Test
    void executesNormalFlowUsingMocks() throws Exception {
        File pdf = createDummyPdf();

        List<Paragraph> embeddedParagraphs = List.of(
                new Paragraph("Armor Class 16", 1, 100, 100, "Font", 10),
                new Paragraph("Hit Points 20", 1, 100, 120, "Font", 10)
        );

        Map<Integer, PageMeta> layout = Map.of(1, new PageMeta(595, 842, 50, 50, 1, "Font", 10));

        when(embeddedExtractor.extract(pdf.getAbsolutePath()))
                .thenReturn(new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, layout));
        when(translatorGateway.translate(any(), eq("Spanish")))
                .thenAnswer(inv -> "ES:" + inv.getArgument(0, String.class));

        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                (paragraphs, layoutInfo) -> false,
                new TextSanitizer(),
                new GlossaryService(List.of(
                        new GlossaryEntry("Armor Class", "Clase de Armadura", false),
                        new GlossaryEntry("Hit Points", "Puntos de Golpe", false)
                )),
                new ParagraphTranslationExecutor(1),
                translatorGateway,
                pdfRebuilderGateway,
                embeddedExtractor,
                ocrExtractor,
                () -> {
                }
        );

        TranslationResult result = coordinator.execute(new TranslationRequest(pdf, "Spanish"), new SilentListener());

        assertFalse(result.usedOcrFallback());
        assertEquals(2, result.paragraphCount());

        verify(embeddedExtractor).extract(pdf.getAbsolutePath());
        verify(translatorGateway).translate("DNDTERM0X 16", "Spanish");
        verify(translatorGateway).translate("DNDTERM0X 20", "Spanish");
        verifyNoInteractions(ocrExtractor);

        ArgumentCaptor<List<Paragraph>> paragraphsCaptor = ArgumentCaptor.forClass(List.class);
        verify(pdfRebuilderGateway).rebuild(eq(pdf.getAbsolutePath()), paragraphsCaptor.capture(), eq(layout));

        List<Paragraph> rebuiltParagraphs = paragraphsCaptor.getValue();
        assertEquals("ES:Clase de Armadura 16", rebuiltParagraphs.get(0).getTranslatedText());
        assertEquals("ES:Puntos de Golpe 20", rebuiltParagraphs.get(1).getTranslatedText());
    }

    @Test
    void usesOcrWhenDecisionRequiresFallback() throws Exception {
        File pdf = createDummyPdf();

        List<Paragraph> embeddedParagraphs = List.of(new Paragraph("bad", 1, 100, 100, "Font", 10));
        List<Paragraph> ocrParagraphs = List.of(new Paragraph("Scanned line", 1, 120, 150, "Font", 10));
        Map<Integer, PageMeta> layout = Map.of(1, new PageMeta(595, 842, 50, 50, 1, "Font", 10));

        when(embeddedExtractor.extract(pdf.getAbsolutePath()))
                .thenReturn(new TranslationCoordinatorService.ExtractionSnapshot(embeddedParagraphs, layout));
        when(ocrExtractor.extract(pdf))
                .thenReturn(new TranslationCoordinatorService.ExtractionSnapshot(ocrParagraphs, layout));
        when(translatorGateway.translate(any(), eq("Spanish")))
                .thenReturn("TR:Scanned line");

        TranslationCoordinatorService coordinator = new TranslationCoordinatorService(
                (paragraphs, layoutInfo) -> true,
                new TextSanitizer(),
                new GlossaryService(List.of()),
                new ParagraphTranslationExecutor(1),
                translatorGateway,
                pdfRebuilderGateway,
                embeddedExtractor,
                ocrExtractor,
                () -> {
                }
        );

        TranslationResult result = coordinator.execute(new TranslationRequest(pdf, "Spanish"), new SilentListener());

        assertTrue(result.usedOcrFallback());
        verify(ocrExtractor).extract(pdf);
    }

    private File createDummyPdf() throws Exception {
        Path pdfPath = tempDir.resolve("sample.pdf");
        Files.writeString(pdfPath, "dummy");
        return pdfPath.toFile();
    }

    private static class SilentListener implements TranslationEventListener {
        @Override
        public void onLog(String message) {
        }
    }
}
