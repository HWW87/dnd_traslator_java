package com.dndtranslator.service.workflow;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Metricas de calidad para una corrida de traduccion.
 */
public final class QualityRunMetrics {

    private int totalUnits;
    private int translatedUnits;
    private int fallbackUnits;
    private int resumedUnits;
    private int retries;
    private boolean usedOcrFallback;

    public void setTotalUnits(int totalUnits) {
        this.totalUnits = Math.max(0, totalUnits);
    }

    public void setTranslatedUnits(int translatedUnits) {
        this.translatedUnits = Math.max(0, translatedUnits);
    }

    public void setUsedOcrFallback(boolean usedOcrFallback) {
        this.usedOcrFallback = usedOcrFallback;
    }

    public void addResumedUnits(int resumedUnits) {
        this.resumedUnits += Math.max(0, resumedUnits);
    }

    public void addFallbackUnits(int fallbackUnits) {
        this.fallbackUnits += Math.max(0, fallbackUnits);
    }

    public void addRetries(int retries) {
        this.retries += Math.max(0, retries);
    }

    public Map<String, Object> asMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("total_units", totalUnits);
        map.put("translated_units_runtime", translatedUnits);
        map.put("fallback_units", fallbackUnits);
        map.put("resumed_units", resumedUnits);

        // Backward-compatible aliases for existing dashboards/tests still using paragraph naming.
        map.put("total_paragraphs", totalUnits);
        map.put("translated_paragraphs", translatedUnits);
        map.put("fallback_paragraphs", fallbackUnits);
        map.put("resumed_paragraphs", resumedUnits);

        map.put("retry_count", retries);
        map.put("used_ocr_fallback", usedOcrFallback);
        return map;
    }

    public void setTotalParagraphs(int totalParagraphs) {
        setTotalUnits(totalParagraphs);
    }

    public void setTranslatedParagraphs(int translatedParagraphs) {
        setTranslatedUnits(translatedParagraphs);
    }

    public void addResumedParagraphs(int resumedParagraphs) {
        addResumedUnits(resumedParagraphs);
    }

    public void addFallbackParagraphs(int fallbackParagraphs) {
        addFallbackUnits(fallbackParagraphs);
    }
}

