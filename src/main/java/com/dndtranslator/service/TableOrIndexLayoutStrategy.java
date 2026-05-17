package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TableOrIndexLayoutStrategy extends BasePageLayoutStrategy {

    private static final float MIN_BOX_WIDTH = 120f;
    private static final float MIN_BOX_HEIGHT = 40f;
    private static final float MIN_BOX_HEIGHT_INDEX = 28f;
    private static final float MIN_BOX_HEIGHT_TABLE = 34f;
    private static final float DOMINANT_VISUAL_MIN_PAGE_RATIO = 0.18f;

    public TableOrIndexLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        super(pageLayoutBuilder);
    }

    @Override
    public void renderPage(PageRenderContext context) {
        PageMeta meta = context.getPageMeta();
        PageAnalysisData analysisData = context.getAnalysisData();
        StrategyMargins margins = resolveMargins(meta, TABLE_INDEX_MIN_MARGIN, TABLE_INDEX_MIN_MARGIN);
        float minBoxHeight = resolveMinBoxHeight(analysisData);
        boolean indexDense = isIndexDense(analysisData);
        boolean tableDense = isTableDense(analysisData);

        List<BlockedRegion> blockedRegions = toBlockedRegions(context.getPageImages());
        List<LayoutBox> boxes = new ArrayList<>();

        if (blockedRegions.isEmpty()) {
            boxes.add(buildFullFlowBox(meta, margins));
            context.setPageLayout(new PageLayout(boxes, blockedRegions));
            return;
        }

        BlockedRegion mainVisual = blockedRegions.stream()
                .max(Comparator.comparing(region -> region.width() * region.height()))
                .orElse(null);

        if (mainVisual == null) {
            boxes.add(buildFullFlowBox(meta, margins));
            context.setPageLayout(new PageLayout(boxes, blockedRegions));
            return;
        }

        if (!isDominantVisual(mainVisual, meta)) {
            boxes.add(buildFullFlowBox(meta, margins));
            context.setPageLayout(new PageLayout(boxes, blockedRegions));
            return;
        }

        float pageWidth = meta.getWidth();
        float pageHeight = meta.getHeight();
        float usableWidth = Math.max(1f, pageWidth - margins.left() - margins.right());
        float usableTop = pageHeight - margins.top();

        LayoutBox topBox = new LayoutBox(
                margins.left(),
                margins.bottom(),
                usableWidth,
                Math.max(0f, mainVisual.y() - margins.bottom() - VISUAL_REGION_PADDING)
        );

        LayoutBox bottomBox = new LayoutBox(
                margins.left(),
                mainVisual.top() + VISUAL_REGION_PADDING,
                usableWidth,
                Math.max(0f, usableTop - (mainVisual.top() + VISUAL_REGION_PADDING))
        );

        LayoutBox leftBox = new LayoutBox(
                margins.left(),
                mainVisual.y(),
                Math.max(0f, mainVisual.x() - margins.left() - VISUAL_REGION_PADDING),
                mainVisual.height()
        );

        LayoutBox rightBox = new LayoutBox(
                mainVisual.right() + VISUAL_REGION_PADDING,
                mainVisual.y(),
                Math.max(0f, (pageWidth - margins.right()) - (mainVisual.right() + VISUAL_REGION_PADDING)),
                mainVisual.height()
        );

        addIfValid(boxes, topBox, minBoxHeight);
        addIfValid(boxes, bottomBox, minBoxHeight);
        if (!indexDense && !tableDense) {
            addIfValid(boxes, leftBox, minBoxHeight);
            addIfValid(boxes, rightBox, minBoxHeight);
        }

        if (boxes.isEmpty()) {
            boxes.add(buildFullFlowBox(meta, margins));
        }

        context.setPageLayout(new PageLayout(sortTopDownLeftRight(boxes), blockedRegions));
    }

    private LayoutBox buildFullFlowBox(PageMeta meta, StrategyMargins margins) {
        return new LayoutBox(
                margins.left(),
                margins.bottom(),
                Math.max(1f, meta.getWidth() - margins.left() - margins.right()),
                Math.max(1f, meta.getHeight() - margins.top() - margins.bottom())
        );
    }

    private void addIfValid(List<LayoutBox> boxes, LayoutBox box, float minBoxHeight) {
        if (box.width() >= MIN_BOX_WIDTH && box.height() >= minBoxHeight) {
            boxes.add(box);
        }
    }

    private float resolveMinBoxHeight(PageAnalysisData data) {
        if (isIndexDense(data)) {
            return MIN_BOX_HEIGHT_INDEX;
        }
        if (isTableDense(data)) {
            return MIN_BOX_HEIGHT_TABLE;
        }
        return MIN_BOX_HEIGHT;
    }

    private boolean isIndexDense(PageAnalysisData data) {
        if (data == null) {
            return false;
        }
        int safeLineCount = Math.max(1, data.lineCount());
        float shortLineRatio = (float) data.shortLineCount() / safeLineCount;
        return (data.hasIndexLikePatterns() || data.hasDottedLeaderPatterns())
                && data.shortLineCount() >= 10
                && shortLineRatio >= 0.55f;
    }

    private boolean isTableDense(PageAnalysisData data) {
        if (data == null) {
            return false;
        }
        int safeLineCount = Math.max(1, data.lineCount());
        float numericRatio = (float) Math.max(0, data.shortLineCount() - data.longLineCount()) / safeLineCount;
        return (data.hasManyNumericLines() || data.hasTableLikePatterns())
                && data.textBlockCount() >= 4
                && numericRatio >= 0.30f;
    }

    private boolean isDominantVisual(BlockedRegion visual, PageMeta meta) {
        float pageArea = Math.max(1f, meta.getWidth() * meta.getHeight());
        float visualArea = Math.max(0f, visual.width() * visual.height());
        return (visualArea / pageArea) >= DOMINANT_VISUAL_MIN_PAGE_RATIO;
    }
}