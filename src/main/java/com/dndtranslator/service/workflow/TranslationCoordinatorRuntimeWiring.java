package com.dndtranslator.service.workflow;

import com.dndtranslator.model.Paragraph;
import com.dndtranslator.service.PdfExtractorService;
import com.dndtranslator.service.PdfRebuilderService;
import com.dndtranslator.service.PdfToParagraphService;
import com.dndtranslator.service.SqliteCheckpointStore;
import com.dndtranslator.service.TranslatorService;

import java.util.List;

/**
 * Encapsula el wiring runtime del workflow para evitar mezclar composición
 * de infraestructura con la lógica de orquestación.
 */
final class TranslationCoordinatorRuntimeWiring {

    private TranslationCoordinatorRuntimeWiring() {
    }

    static RuntimeDependencies defaultDependencies() {
        TranslatorService translatorService = new TranslatorService();
        return fromServices(
                translatorService,
                new PdfRebuilderService(),
                new OcrDecisionService(),
                new TextSanitizer(),
                new GlossaryService(),
                new ParagraphTranslationExecutor(),
                new SqliteCheckpointStore()
        );
    }

    static RuntimeDependencies fromServices(
            TranslatorService translatorService,
            PdfRebuilderService pdfRebuilderService,
            OcrDecisionService ocrDecisionService,
            TextSanitizer textSanitizer,
            GlossaryService glossaryService,
            ParagraphTranslationExecutor paragraphTranslationExecutor,
            CheckpointStore checkpointStore
    ) {
        return new RuntimeDependencies(
                ocrDecisionService::shouldUseOcrFallback,
                textSanitizer,
                glossaryService,
                paragraphTranslationExecutor,
                translatorService::translate,
                translatorService::translateUnit,
                pdfRebuilderService::rebuild,
                pdfPath -> {
                    PdfExtractorService extractor = new PdfExtractorService();
                    List<Paragraph> paragraphs = extractor.extractParagraphs(pdfPath);
                    return new TranslationCoordinatorService.ExtractionSnapshot(paragraphs, extractor.getLayoutInfo());
                },
                pdfFile -> {
                    PdfToParagraphService extractor = new PdfToParagraphService();
                    List<Paragraph> paragraphs = extractor.extractParagraphsFromPdf(pdfFile);
                    return new TranslationCoordinatorService.ExtractionSnapshot(paragraphs, extractor.getLayoutInfo());
                },
                translatorService::shutdown,
                checkpointStore == null ? CheckpointStore.noop() : checkpointStore
        );
    }

    record RuntimeDependencies(
            TranslationCoordinatorService.OcrDecisionPort ocrDecisionPort,
            TextSanitizer textSanitizer,
            GlossaryService glossaryService,
            ParagraphTranslationExecutor paragraphTranslationExecutor,
            TranslationCoordinatorService.TranslatorGateway translatorGateway,
            TranslationCoordinatorService.UnitTranslatorGateway unitTranslatorGateway,
            TranslationCoordinatorService.PdfRebuilderGateway pdfRebuilderGateway,
            TranslationCoordinatorService.EmbeddedExtractor embeddedExtractor,
            TranslationCoordinatorService.OcrExtractor ocrExtractor,
            Runnable shutdownHook,
            CheckpointStore checkpointStore
    ) {
    }
}

