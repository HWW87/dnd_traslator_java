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

        for (Paragraph paragraph : paragraphs) {
            if (paragraph == null) {
                continue;
            }
            units.add(toUnit(paragraph, targetLanguage));
        }
        return units;
    }

    public TranslationUnit toUnit(Paragraph paragraph, String targetLanguage) {
        String sourceText = paragraph.getFullText() == null ? "" : paragraph.getFullText();
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
        return unit;
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

