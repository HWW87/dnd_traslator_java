package com.dndtranslator.service.workflow;

public record TranslationResult(String outputPdfPath, int paragraphCount, boolean usedOcrFallback) {
}

