package com.dndtranslator.service;

public record PageAnalysisData(
        int pageNumber,
        float pageWidth,
        float pageHeight,
        int imageCount,
        float estimatedImageAreaRatio,
        int textBlockCount,
        int lineCount,
        int wordCount,
        int shortLineCount,
        int longLineCount,
        boolean hasLargeImage,
        boolean hasManyNumericLines,
        boolean hasMapLikeKeywords,
        boolean hasIndexLikePatterns,
        boolean hasTitleLikePatterns,
        boolean hasVeryLowTextDensity
) {
}

