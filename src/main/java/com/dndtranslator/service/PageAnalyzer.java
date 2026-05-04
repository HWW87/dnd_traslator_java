package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;

import java.util.List;
import java.util.Locale;

public class PageAnalyzer {

    private static final List<String> MAP_KEYWORDS = List.of(
            "map", "quadrant", "sector", "route", "base", "zone", "frontier", "region",
            "mapa", "cuadrante", "sector", "base", "zona", "region", "región"
    );

    private static final List<String> TITLE_HINTS = List.of(
            "chapter", "capitulo", "capítulo", "introduction", "introduccion", "introducción",
            "part", "volume", "manual", "guide", "guia", "guía"
    );

    public PageAnalysisData analyze(
            int pageNumber,
            PageMeta pageMeta,
            List<Paragraph> pageParagraphs,
            List<PdfImagePlacement> pageImages
    ) {
        float pageWidth = pageMeta != null ? pageMeta.getWidth() : 612f;
        float pageHeight = pageMeta != null ? pageMeta.getHeight() : 792f;
        float pageArea = Math.max(1f, pageWidth * pageHeight);

        int imageCount = pageImages == null ? 0 : pageImages.size();
        float imageArea = 0f;
        boolean hasLargeImage = false;

        if (pageImages != null) {
            for (PdfImagePlacement image : pageImages) {
                if (image == null) {
                    continue;
                }
                float area = Math.max(0f, image.width() * image.height());
                imageArea += area;
                if (area / pageArea >= 0.28f) {
                    hasLargeImage = true;
                }
            }
        }

        int textBlockCount = pageParagraphs == null ? 0 : pageParagraphs.size();
        int lineCount = 0;
        int wordCount = 0;
        int shortLineCount = 0;
        int longLineCount = 0;
        int numericLineCount = 0;

        StringBuilder combinedText = new StringBuilder();
        if (pageParagraphs != null) {
            for (Paragraph paragraph : pageParagraphs) {
                if (paragraph == null) {
                    continue;
                }
                String text = paragraph.getFullText();
                if (text == null || text.isBlank()) {
                    continue;
                }

                String[] logicalLines = text.split("\\r?\\n");
                for (String line : logicalLines) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        continue;
                    }
                    lineCount++;

                    int wordsInLine = countWords(trimmed);
                    wordCount += wordsInLine;

                    if (trimmed.length() <= 34) {
                        shortLineCount++;
                    }
                    if (trimmed.length() >= 78 || wordsInLine >= 12) {
                        longLineCount++;
                    }
                    if (looksNumericLine(trimmed)) {
                        numericLineCount++;
                    }

                    combinedText.append(trimmed).append('\n');
                }
            }
        }

        String lowerText = combinedText.toString().toLowerCase(Locale.ROOT);
        float imageRatio = Math.min(1f, imageArea / pageArea);

        boolean hasMapLikeKeywords = containsAnyKeyword(lowerText, MAP_KEYWORDS);
        boolean hasIndexLikePatterns = detectIndexPatterns(lowerText, shortLineCount, numericLineCount, lineCount);
        boolean hasTitleLikePatterns = detectTitlePatterns(lowerText, lineCount, longLineCount);
        boolean hasManyNumericLines = lineCount >= 4 && ((float) numericLineCount / Math.max(1, lineCount)) >= 0.35f;
        boolean hasVeryLowTextDensity = wordCount <= 70 || (lineCount <= 8 && wordCount <= 110);

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

    private int countWords(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }

    private boolean looksNumericLine(String line) {
        int digits = 0;
        int letters = 0;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (Character.isDigit(ch)) {
                digits++;
            } else if (Character.isLetter(ch)) {
                letters++;
            }
        }
        if (digits == 0) {
            return false;
        }
        return digits >= 2 && (letters == 0 || (float) digits / (digits + letters) >= 0.30f);
    }

    private boolean containsAnyKeyword(String text, List<String> keywords) {
        if (text == null || text.isBlank()) {
            return false;
        }
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private boolean detectIndexPatterns(String lowerText, int shortLineCount, int numericLineCount, int lineCount) {
        if (lineCount == 0) {
            return false;
        }
        boolean dottedEntries = lowerText.contains("...") || lowerText.contains(" . ");
        boolean indexHeaders = lowerText.contains("index") || lowerText.contains("contents") || lowerText.contains("indice") || lowerText.contains("índice");
        boolean shortDense = shortLineCount >= 10 && ((float) shortLineCount / lineCount) >= 0.55f;
        boolean numericDense = numericLineCount >= 4 && ((float) numericLineCount / lineCount) >= 0.30f;
        return indexHeaders || (shortDense && numericDense) || (dottedEntries && shortDense);
    }

    private boolean detectTitlePatterns(String lowerText, int lineCount, int longLineCount) {
        if (containsAnyKeyword(lowerText, TITLE_HINTS)) {
            return true;
        }
        return lineCount <= 6 && longLineCount <= 1;
    }
}

