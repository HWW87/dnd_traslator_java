package com.dndtranslator.service.workflow;

public class TextSanitizer {

    public String sanitizeForTranslation(String sourceText) {
        if (sourceText == null || sourceText.isBlank()) {
            return "";
        }
        return sourceText.replaceAll("[^\\p{L}\\p{N}\\s.,;:!?¿¡()\\[\\]\\-]", "").trim();
    }
}

