package com.dndtranslator.service;

public class PageTypeClassifier {

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
        boolean firstPage = data.pageNumber() <= 1;
        boolean lowText = data.hasVeryLowTextDensity() || data.wordCount() <= 30;
        boolean visualDominant = data.estimatedImageAreaRatio() >= 0.35f || data.hasLargeImage();
        return lowText && visualDominant && (firstPage || data.hasTitleLikePatterns() || data.lineCount() <= 4);
    }

    private boolean isMapPage(PageAnalysisData data) {
        if (!data.hasMapLikeKeywords()) {
            return false;
        }
        boolean mostlyLabels = data.shortLineCount() >= Math.max(5, data.longLineCount() * 2);
        boolean lowContinuousText = data.longLineCount() <= 2 || data.wordCount() <= 100;
        boolean visualComposition = data.estimatedImageAreaRatio() >= 0.20f || data.hasLargeImage();
        return mostlyLabels && lowContinuousText && visualComposition;
    }

    private boolean isTableOrIndex(PageAnalysisData data) {
        boolean manyShortLines = data.shortLineCount() >= 10;
        boolean sparseLongLines = data.longLineCount() <= Math.max(2, data.lineCount() / 5);
        return data.hasIndexLikePatterns() || (manyShortLines && data.hasManyNumericLines() && sparseLongLines);
    }

    private boolean isImageHeavy(PageAnalysisData data) {
        boolean highImageRatio = data.estimatedImageAreaRatio() >= 0.55f;
        boolean lowText = data.wordCount() <= 90 && data.longLineCount() <= 3;
        return (highImageRatio || data.hasLargeImage()) && lowText;
    }

    private boolean isTextHeavy(PageAnalysisData data) {
        boolean manyWords = data.wordCount() >= 180;
        boolean manyLongLines = data.longLineCount() >= 8;
        boolean lowImage = data.estimatedImageAreaRatio() <= 0.18f;
        return (manyWords || manyLongLines) && lowImage;
    }

    private boolean isMixedLayout(PageAnalysisData data) {
        boolean hasText = data.wordCount() >= 40;
        boolean hasVisual = data.estimatedImageAreaRatio() >= 0.18f || data.imageCount() > 0;
        return hasText && hasVisual;
    }
}

