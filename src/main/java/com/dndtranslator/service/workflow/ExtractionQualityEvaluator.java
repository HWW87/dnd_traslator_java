package com.dndtranslator.service.workflow;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;

import java.util.List;
import java.util.Map;

/**
 * Adaptador de compatibilidad para mantener contratos existentes.
 * La lógica real de decisión OCR vive en OcrDecisionService.
 */
public class ExtractionQualityEvaluator {

    private final OcrDecisionService delegate = new OcrDecisionService();

    public boolean shouldUseOcrFallback(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) {
        return delegate.shouldUseOcrFallback(paragraphs, layoutInfo);
    }
}
