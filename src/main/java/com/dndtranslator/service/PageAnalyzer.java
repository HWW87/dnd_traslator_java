package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public class PageAnalyzer {

    private static final float DEFAULT_PAGE_WIDTH = 612f;
    private static final float DEFAULT_PAGE_HEIGHT = 792f;
    private static final float LARGE_IMAGE_MIN_RATIO = 0.30f;

    private static final Set<String> MAP_KEYWORDS = Set.of(
            "map", "quadrant", "sector", "base", "route", "region", "zone", "legend",
            "area", "district", "outpost", "frontier", "colony", "grid", "coordinates",
            "mapa", "cuadrante", "ruta", "región", "zona", "leyenda"
    );

    private static final Set<String> TITLE_KEYWORDS = Set.of(
            "chapter", "capitulo", "capítulo", "cover", "titulo", "título", "edition", "volumen",
            "volume", "book", "part", "section", "appendix", "introduction", "foreword",
            "module", "scenario", "seccion", "sección", "apendice", "apéndice", "introduccion", "introducción"
    );

    public PageAnalysisData analyze(
            int pageNumber,
            PageMeta meta,
            List<PdfImagePlacement> images,
            List<Paragraph> paragraphs
    ) {
        float pageWidth = meta != null ? meta.getWidth() : DEFAULT_PAGE_WIDTH;
        float pageHeight = meta != null ? meta.getHeight() : DEFAULT_PAGE_HEIGHT;
        float pageArea = Math.max(1f, pageWidth * pageHeight);

        int imageCount = 0;
        float imageArea = 0f;
        boolean hasLargeImage = false;

        if (images != null) {
            for (PdfImagePlacement image : images) {
                if (image == null || !image.isRenderable()) {
                    continue;
                }
                imageCount++;
                float currentArea = Math.max(0f, image.width() * image.height());
                imageArea += currentArea;
                if ((currentArea / pageArea) >= LARGE_IMAGE_MIN_RATIO) {
                    hasLargeImage = true;
                }
            }
        }

        int textBlockCount = 0;
        int lineCount = 0;
        int shortLineCount = 0;
        int longLineCount = 0;
        int wordCount = 0;
        int numericLikeLines = 0;
        int mapKeywordHits = 0;
        int titleKeywordHits = 0;
        int indexPatternHits = 0;

        if (paragraphs != null) {
            for (Paragraph paragraph : paragraphs) {
                if (paragraph == null) {
                    continue;
                }

                String text = bestText(paragraph);
                if (text.isBlank()) {
                    continue;
                }

                textBlockCount++;
                lineCount++;

                int words = countWords(text);
                wordCount += words;

                if (words <= 4 || text.length() <= 20) {
                    shortLineCount++;
                }
                if (words >= 10 || text.length() >= 70) {
                    longLineCount++;
                }
                if (looksNumericLine(text)) {
                    numericLikeLines++;
                }
                if (looksIndexLine(text)) {
                    indexPatternHits++;
                }

                String normalized = text.toLowerCase(Locale.ROOT);
                if (containsAnyKeyword(normalized, MAP_KEYWORDS)) {
                    mapKeywordHits++;
                }
                if (containsAnyKeyword(normalized, TITLE_KEYWORDS)) {
                    titleKeywordHits++;
                }
            }
        }

        float imageRatio = clamp(imageArea / pageArea, 0f, 1f);
        boolean hasManyNumericLines = lineCount > 0 && numericLikeLines >= Math.max(3, lineCount / 3);
        boolean hasMapLikeKeywords = mapKeywordHits >= 2;
        boolean hasIndexLikePatterns = indexPatternHits >= 3 || (shortLineCount >= 8 && hasManyNumericLines);
        boolean hasTitleLikePatterns = titleKeywordHits > 0 || (lineCount <= 3 && wordCount <= 18);
        boolean hasVeryLowTextDensity = wordCount <= 45 && lineCount <= 8;

        return new PageAnalysisData(
                pageNumber,
                pageWidth,
                pageHeight,
                imageCount,
                imageRatio,
                textBlockCount,
                lineCount,
                wordCount,
                shortLineCount,
                longLineCount,
                hasLargeImage,
                hasManyNumericLines,
                hasMapLikeKeywords,
                hasIndexLikePatterns,
                hasTitleLikePatterns,
                hasVeryLowTextDensity
        );
    }

    private String bestText(Paragraph paragraph) {
        String original = paragraph.getFullText();
        if (original != null && !original.isBlank()) {
            return normalize(original);
        }

        String translated = paragraph.getTranslatedText();
        if (translated != null && !translated.isBlank()) {
            return normalize(translated);
        }

        return "";
    }

    private String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private boolean looksNumericLine(String text) {
        String compact = text.replace(" ", "");
        if (compact.isEmpty()) {
            return false;
        }
        long digitCount = compact.chars().filter(Character::isDigit).count();
        return digitCount >= 3 && ((double) digitCount / compact.length()) >= 0.35d;
    }

    private boolean looksIndexLine(String text) {
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        return normalized.matches(".*\\.{2,}\\s*\\d+$")
                || normalized.matches("^\\d+[\\.)-].*")
                || normalized.matches(".*\\s\\d{1,4}$");
    }

    private boolean containsAnyKeyword(String text, Set<String> keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}