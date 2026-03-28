package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageTypeClassifierTest {

    private final PageTypeClassifier classifier = new PageTypeClassifier();

    @Test
    void classifiesTextHeavyPage() {
        PageAnalysisData data = baseDataBuilder()
                .wordCount(260)
                .lineCount(26)
                .longLineCount(15)
                .shortLineCount(3)
                .estimatedImageAreaRatio(0.05f)
                .build();

        assertEquals(PageType.TEXT_HEAVY, classifier.classify(data));
    }

    @Test
    void classifiesMapPage() {
        PageAnalysisData data = baseDataBuilder()
                .estimatedImageAreaRatio(0.38f)
                .hasLargeImage(true)
                .shortLineCount(14)
                .longLineCount(1)
                .wordCount(75)
                .hasMapLikeKeywords(true)
                .build();

        assertEquals(PageType.MAP_PAGE, classifier.classify(data));
    }

    @Test
    void classifiesTableOrIndexPage() {
        PageAnalysisData data = baseDataBuilder()
                .lineCount(24)
                .shortLineCount(18)
                .longLineCount(1)
                .wordCount(90)
                .hasManyNumericLines(true)
                .hasIndexLikePatterns(true)
                .build();

        assertEquals(PageType.TABLE_OR_INDEX, classifier.classify(data));
    }

    @Test
    void classifiesTitleOrCoverPage() {
        PageAnalysisData data = baseDataBuilder()
                .pageNumber(1)
                .wordCount(12)
                .lineCount(2)
                .estimatedImageAreaRatio(0.45f)
                .hasLargeImage(true)
                .hasTitleLikePatterns(true)
                .hasVeryLowTextDensity(true)
                .build();

        assertEquals(PageType.TITLE_OR_COVER, classifier.classify(data));
    }

    @Test
    void classifiesTitleOrCoverForZeroBasedFirstPage() {
        PageAnalysisData data = baseDataBuilder()
                .pageNumber(0)
                .wordCount(16)
                .lineCount(3)
                .estimatedImageAreaRatio(0.50f)
                .hasLargeImage(true)
                .hasVeryLowTextDensity(true)
                .build();

        assertEquals(PageType.TITLE_OR_COVER, classifier.classify(data));
    }

    @Test
    void classifiesImageHeavyPage() {
        PageAnalysisData data = baseDataBuilder()
                .wordCount(45)
                .longLineCount(2)
                .estimatedImageAreaRatio(0.62f)
                .hasLargeImage(true)
                .build();

        assertEquals(PageType.IMAGE_HEAVY, classifier.classify(data));
    }

    @Test
    void classifiesMixedLayoutWhenImageAndTextAreBalanced() {
        PageAnalysisData data = baseDataBuilder()
                .wordCount(95)
                .longLineCount(4)
                .shortLineCount(5)
                .estimatedImageAreaRatio(0.24f)
                .hasLargeImage(false)
                .hasMapLikeKeywords(false)
                .hasIndexLikePatterns(false)
                .hasManyNumericLines(false)
                .build();

        assertEquals(PageType.MIXED_LAYOUT, classifier.classify(data));
    }

    @Test
    void titleOrCoverHasPriorityOverImageHeavySignals() {
        PageAnalysisData data = baseDataBuilder()
                .pageNumber(1)
                .wordCount(10)
                .lineCount(2)
                .longLineCount(1)
                .estimatedImageAreaRatio(0.70f)
                .hasLargeImage(true)
                .hasTitleLikePatterns(true)
                .hasVeryLowTextDensity(true)
                .build();

        assertEquals(PageType.TITLE_OR_COVER, classifier.classify(data));
    }

    @Test
    void mapHasPriorityOverTableSignalsWhenMapKeywordsExist() {
        PageAnalysisData data = baseDataBuilder()
                .estimatedImageAreaRatio(0.36f)
                .hasLargeImage(true)
                .shortLineCount(16)
                .longLineCount(1)
                .wordCount(70)
                .hasMapLikeKeywords(true)
                .hasIndexLikePatterns(true)
                .hasManyNumericLines(true)
                .build();

        assertEquals(PageType.MAP_PAGE, classifier.classify(data));
    }

    @Test
    void tableOrIndexHasPriorityWhenMapLikeShapeLacksMapSignals() {
        PageAnalysisData data = baseDataBuilder()
                .estimatedImageAreaRatio(0.34f)
                .hasLargeImage(true)
                .lineCount(20)
                .shortLineCount(14)
                .longLineCount(2)
                .wordCount(92)
                .hasMapLikeKeywords(false)
                .hasManyNumericLines(true)
                .hasIndexLikePatterns(true)
                .build();

        assertEquals(PageType.TABLE_OR_INDEX, classifier.classify(data));
    }

    @Test
    void classifiesMapWithoutKeywordsWhenVisualLabelsAreStrongAndNotNumeric() {
        PageAnalysisData data = baseDataBuilder()
                .estimatedImageAreaRatio(0.33f)
                .hasLargeImage(true)
                .lineCount(12)
                .shortLineCount(10)
                .longLineCount(1)
                .wordCount(58)
                .textBlockCount(10)
                .hasMapLikeKeywords(false)
                .hasManyNumericLines(false)
                .hasIndexLikePatterns(false)
                .build();

        assertEquals(PageType.MAP_PAGE, classifier.classify(data));
    }

    @Test
    void classifiesUnknownWhenNoStrongSignalExists() {
        PageAnalysisData data = baseDataBuilder()
                .imageCount(0)
                .wordCount(20)
                .lineCount(6)
                .shortLineCount(3)
                .longLineCount(2)
                .estimatedImageAreaRatio(0.05f)
                .hasLargeImage(false)
                .hasMapLikeKeywords(false)
                .hasIndexLikePatterns(false)
                .hasManyNumericLines(false)
                .hasTitleLikePatterns(false)
                .hasVeryLowTextDensity(false)
                .build();

        assertEquals(PageType.UNKNOWN, classifier.classify(data));
    }

    private PageAnalysisDataBuilder baseDataBuilder() {
        return new PageAnalysisDataBuilder();
    }

    private static class PageAnalysisDataBuilder {
        private int pageNumber = 2;
        private float pageWidth = 600f;
        private float pageHeight = 800f;
        private int imageCount = 1;
        private float estimatedImageAreaRatio = 0.1f;
        private int textBlockCount = 10;
        private int lineCount = 10;
        private int wordCount = 80;
        private int shortLineCount = 4;
        private int longLineCount = 4;
        private boolean hasLargeImage;
        private boolean hasManyNumericLines;
        private boolean hasMapLikeKeywords;
        private boolean hasIndexLikePatterns;
        private boolean hasTitleLikePatterns;
        private boolean hasVeryLowTextDensity;

        PageAnalysisDataBuilder pageNumber(int value) { this.pageNumber = value; return this; }
        PageAnalysisDataBuilder imageCount(int value) { this.imageCount = value; return this; }
        PageAnalysisDataBuilder estimatedImageAreaRatio(float value) { this.estimatedImageAreaRatio = value; return this; }
        PageAnalysisDataBuilder textBlockCount(int value) { this.textBlockCount = value; return this; }
        PageAnalysisDataBuilder lineCount(int value) { this.lineCount = value; return this; }
        PageAnalysisDataBuilder wordCount(int value) { this.wordCount = value; return this; }
        PageAnalysisDataBuilder shortLineCount(int value) { this.shortLineCount = value; return this; }
        PageAnalysisDataBuilder longLineCount(int value) { this.longLineCount = value; return this; }
        PageAnalysisDataBuilder hasLargeImage(boolean value) { this.hasLargeImage = value; return this; }
        PageAnalysisDataBuilder hasManyNumericLines(boolean value) { this.hasManyNumericLines = value; return this; }
        PageAnalysisDataBuilder hasMapLikeKeywords(boolean value) { this.hasMapLikeKeywords = value; return this; }
        PageAnalysisDataBuilder hasIndexLikePatterns(boolean value) { this.hasIndexLikePatterns = value; return this; }
        PageAnalysisDataBuilder hasTitleLikePatterns(boolean value) { this.hasTitleLikePatterns = value; return this; }
        PageAnalysisDataBuilder hasVeryLowTextDensity(boolean value) { this.hasVeryLowTextDensity = value; return this; }

        PageAnalysisData build() {
            return new PageAnalysisData(
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
                    hasTitleLikePatterns,
                    hasVeryLowTextDensity
            );
        }
    }
}

