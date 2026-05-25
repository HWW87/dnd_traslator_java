package com.dndtranslator.service.workflow;

import com.dndtranslator.domain.TranslationUnit;
import com.dndtranslator.domain.UnitType;
import com.dndtranslator.model.Paragraph;

import java.util.ArrayList;
import java.util.List;

/**
 * Convierte parrafos extraidos del PDF en unidades canonicas de traduccion.
 *
 * Phase 11: Canonical work unit migration
 */
public class ParagraphToUnitConverter {

    public List<TranslationUnit> convert(List<Paragraph> paragraphs, String targetLanguage) {
        List<TranslationUnit> units = new ArrayList<>();
        if (paragraphs == null || paragraphs.isEmpty()) {
            return units;
        }

        for (int index = 0; index < paragraphs.size(); index++) {
            Paragraph paragraph = paragraphs.get(index);
            if (paragraph == null) {
                continue;
            }
            units.add(toUnit(paragraph, targetLanguage, index));
        }
        return units;
    }

    public TranslationUnit toUnit(Paragraph paragraph, String targetLanguage) {
        return toUnit(paragraph, targetLanguage, -1);
    }

    private TranslationUnit toUnit(Paragraph paragraph, String targetLanguage, int sourceIndex) {
        String sourceText = normalizeSourceText(paragraph.getFullText());
        TranslationUnit unit = new TranslationUnit(
                paragraph.getPage(),
                sourceText,
                inferUnitType(sourceText),
                targetLanguage == null || targetLanguage.isBlank() ? "Spanish" : targetLanguage
        );

        // Keep deterministic identity hints for checkpoint mapping and diagnostics.
        unit.putMetadata("paragraph_x", paragraph.getX());
        unit.putMetadata("paragraph_y", paragraph.getY());
        unit.putMetadata("paragraph_font", paragraph.getFontName());
        unit.putMetadata("paragraph_font_size", paragraph.getFontSize());
        unit.putMetadata("deterministic_unit_id", buildDeterministicUnitId(paragraph));
        unit.putMetadata("source_text_length", sourceText.length());
        if (sourceIndex >= 0) {
            unit.putMetadata("source_index", sourceIndex);
        }
        return unit;
    }

    private String normalizeSourceText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String normalized = rawText
                .replace('\r', '\n')
                .replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]", " ")
                .replaceAll("[\\uFFFD]", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return normalized;
    }

    private String buildDeterministicUnitId(Paragraph paragraph) {
        String text = paragraph.getFullText();
        String normalizedText = text == null ? "" : text.trim().replaceAll("\\s+", " ");
        int textHash = normalizedText.hashCode();
        int roundedX = Math.round(paragraph.getX());
        int roundedY = Math.round(paragraph.getY());
        return "p" + paragraph.getPage() + "-x" + roundedX + "-y" + roundedY + "-h" + Integer.toHexString(textHash);
    }

    private UnitType inferUnitType(String text) {
        if (text == null || text.isBlank()) {
            return UnitType.UNKNOWN;
        }

        String normalized = text.trim();
        String lower = normalized.toLowerCase();

        if (lower.contains("copyright") || lower.contains("all rights reserved") || lower.contains("license")) {
            return UnitType.LEGAL_TEXT;
        }

        if (normalized.matches(".*\\.{2,}\\s*\\d{1,4}\\s*$")) {
            return UnitType.INDEX_LINE;
        }

        if (normalized.matches("^[A-Z][A-Z\\s\\-]{2,}$") && normalized.length() <= 60) {
            return UnitType.MAP_LABEL;
        }

        if (normalized.length() <= 40) {
            return UnitType.SHORT_LABEL;
        }

        return UnitType.PARAGRAPH;
    }
}

