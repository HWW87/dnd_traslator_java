package com.dndtranslator.service;

public class PageTypeClassifier {

    private static final int FIRST_PAGE_ZERO_BASED = 0;
    private static final int FIRST_PAGE_ONE_BASED = 1;

    private static final int COVER_MAX_WORDS = 30;
    private static final int COVER_MAX_LINES = 4;
    private static final float COVER_MIN_IMAGE_RATIO = 0.35f;

    private static final int MAP_MAX_WORDS = 100;
    private static final int MAP_MAX_LONG_LINES = 2;
    private static final int MAP_MIN_SHORT_LINES = 5;
    private static final float MAP_MIN_IMAGE_RATIO = 0.20f;

    private static final int INDEX_MIN_SHORT_LINES = 10;
    private static final int INDEX_MIN_TEXT_BLOCKS = 4;

    private static final float IMAGE_HEAVY_MIN_RATIO = 0.55f;
    private static final int IMAGE_HEAVY_MAX_WORDS = 90;
    private static final int IMAGE_HEAVY_MAX_LONG_LINES = 3;

    private static final int TEXT_HEAVY_MIN_WORDS = 180;
    private static final int TEXT_HEAVY_MIN_LONG_LINES = 8;
    private static final float TEXT_HEAVY_MAX_IMAGE_RATIO = 0.25f;

    private static final int MIXED_MIN_WORDS = 40;
    private static final float MIXED_MIN_IMAGE_RATIO = 0.18f;

    public PageType classify(PageAnalysisData data) {
        if (data == null) {
            return PageType.UNKNOWN;
        }

        if (isTitleOrCover(data)) {
            return PageType.TITLE_OR_COVER;
        }
        if (isMapPage(data)) {
            return PageType.MAP_PAGE;
        }
        if (isTableOrIndex(data)) {
            return PageType.TABLE_OR_INDEX;
        }
        if (isImageHeavy(data)) {
            return PageType.IMAGE_HEAVY;
        }
        if (isTextHeavy(data)) {
            return PageType.TEXT_HEAVY;
        }
        if (isMixedLayout(data)) {
            return PageType.MIXED_LAYOUT;
        }

        return PageType.UNKNOWN;
    }

    private boolean isTitleOrCover(PageAnalysisData data) {
        boolean firstPage = isFirstPage(data);
        boolean lowText = data.hasVeryLowTextDensity() || data.wordCount() <= COVER_MAX_WORDS;
        boolean visualDominant =
                data.estimatedImageAreaRatio() >= COVER_MIN_IMAGE_RATIO || data.hasLargeImage();
        boolean titleSignals = data.hasTitleLikePatterns();
        boolean veryFewLines = data.lineCount() <= COVER_MAX_LINES;

        return lowText
                && visualDominant
                && (firstPage || titleSignals || (firstPage && veryFewLines));
    }

    private boolean isMapPage(PageAnalysisData data) {
        boolean mostlyLabels =
                data.shortLineCount() >= Math.max(MAP_MIN_SHORT_LINES, data.longLineCount() * 2);
        boolean lowContinuousText =
                data.longLineCount() <= MAP_MAX_LONG_LINES || data.wordCount() <= MAP_MAX_WORDS;
        boolean visualComposition =
                data.estimatedImageAreaRatio() >= MAP_MIN_IMAGE_RATIO || data.hasLargeImage();

        boolean strongKeywordPath =
                data.hasMapLikeKeywords() && mostlyLabels && lowContinuousText && visualComposition;

        boolean heuristicOnlyPath =
                mostlyLabels
                        && lowContinuousText
                        && visualComposition
                        && data.wordCount() <= 80
                        && data.textBlockCount() <= Math.max(6, data.lineCount());

        return strongKeywordPath || heuristicOnlyPath;
    }

    private boolean isTableOrIndex(PageAnalysisData data) {
        boolean manyShortLines = data.shortLineCount() >= INDEX_MIN_SHORT_LINES;
        boolean sparseLongLines = data.longLineCount() <= Math.max(2, data.lineCount() / 5);
        boolean repetitiveStructuredContent =
                manyShortLines
                        && sparseLongLines
                        && data.textBlockCount() >= INDEX_MIN_TEXT_BLOCKS;

        return data.hasIndexLikePatterns()
                || (manyShortLines && data.hasManyNumericLines() && sparseLongLines)
                || repetitiveStructuredContent;
    }

    private boolean isImageHeavy(PageAnalysisData data) {
        boolean highImageRatio = data.estimatedImageAreaRatio() >= IMAGE_HEAVY_MIN_RATIO;
        boolean lowText =
                data.wordCount() <= IMAGE_HEAVY_MAX_WORDS
                        && data.longLineCount() <= IMAGE_HEAVY_MAX_LONG_LINES;

        return (highImageRatio || data.hasLargeImage()) && lowText;
    }

    private boolean isTextHeavy(PageAnalysisData data) {
        boolean manyWords = data.wordCount() >= TEXT_HEAVY_MIN_WORDS;
        boolean manyLongLines = data.longLineCount() >= TEXT_HEAVY_MIN_LONG_LINES;
        boolean lowImage = data.estimatedImageAreaRatio() <= TEXT_HEAVY_MAX_IMAGE_RATIO;

        return (manyWords || manyLongLines) && lowImage;
    }

    private boolean isMixedLayout(PageAnalysisData data) {
        boolean hasText = data.wordCount() >= MIXED_MIN_WORDS;
        boolean hasVisual =
                data.estimatedImageAreaRatio() >= MIXED_MIN_IMAGE_RATIO || data.imageCount() > 0;

        return hasText && hasVisual;
    }

    private boolean isFirstPage(PageAnalysisData data) {
        return data.pageNumber() == FIRST_PAGE_ZERO_BASED
                || data.pageNumber() == FIRST_PAGE_ONE_BASED;
    }
}