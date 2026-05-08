package com.dndtranslator.service.workflow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metricas de calidad para una corrida de traduccion.
 */
public final class QualityRunMetrics {

    private int totalParagraphs;
    private int translatedParagraphs;
    private int fallbackParagraphs;
    private int resumedParagraphs;
    private int retries;
    private boolean usedOcrFallback;

    public void setTotalParagraphs(int totalParagraphs) {
        this.totalParagraphs = Math.max(0, totalParagraphs);
    }

    public void setTranslatedParagraphs(int translatedParagraphs) {
        this.translatedParagraphs = Math.max(0, translatedParagraphs);
    }

    public void setUsedOcrFallback(boolean usedOcrFallback) {
        this.usedOcrFallback = usedOcrFallback;
    }

    public void addResumedParagraphs(int resumedParagraphs) {
        this.resumedParagraphs += Math.max(0, resumedParagraphs);
    }

    public void addFallbackParagraphs(int fallbackParagraphs) {
        this.fallbackParagraphs += Math.max(0, fallbackParagraphs);
    }

    public void addRetries(int retries) {
        this.retries += Math.max(0, retries);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total_paragraphs", totalParagraphs);
        map.put("translated_paragraphs", translatedParagraphs);
        map.put("fallback_paragraphs", fallbackParagraphs);
        map.put("resumed_paragraphs", resumedParagraphs);
        map.put("retry_count", retries);
        map.put("used_ocr_fallback", usedOcrFallback);
        return map;
    }
}

