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
        boolean hasTableLikePatterns,
        boolean hasDottedLeaderPatterns,
        boolean hasTitleLikePatterns,
        boolean hasVeryLowTextDensity
) {
    public PageAnalysisData(
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
        this(
                pageNumber,
                pageWidth,
                pageHeight,
                imageCount,
                estimatedImageAreaRatio,
                textBlockCount,
                lineCount,
                wordCount,
                shortLineCount,
                longLineCount,
                hasLargeImage,
                hasManyNumericLines,
                hasMapLikeKeywords,
                hasIndexLikePatterns,
                false,
                false,
                hasTitleLikePatterns,
                hasVeryLowTextDensity
        );
    }
}

