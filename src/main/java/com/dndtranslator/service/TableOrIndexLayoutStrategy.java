package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TableOrIndexLayoutStrategy extends BasePageLayoutStrategy {

    private static final float MIN_BOX_WIDTH = 120f;
    private static final float MIN_BOX_HEIGHT = 40f;
    private static final float DOMINANT_VISUAL_MIN_PAGE_RATIO = 0.18f;

    public TableOrIndexLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        super(pageLayoutBuilder);
    }

    @Override
    public void renderPage(PageRenderContext context) {
        PageMeta meta = context.getPageMeta();
        StrategyMargins margins = resolveMargins(meta, TABLE_INDEX_MIN_MARGIN, TABLE_INDEX_MIN_MARGIN);

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

        addIfValid(boxes, topBox);
        addIfValid(boxes, bottomBox);
        addIfValid(boxes, leftBox);
        addIfValid(boxes, rightBox);

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

    private void addIfValid(List<LayoutBox> boxes, LayoutBox box) {
        if (box.width() >= MIN_BOX_WIDTH && box.height() >= MIN_BOX_HEIGHT) {
            boxes.add(box);
        }
    }

    private boolean isDominantVisual(BlockedRegion visual, PageMeta meta) {
        float pageArea = Math.max(1f, meta.getWidth() * meta.getHeight());
        float visualArea = Math.max(0f, visual.width() * visual.height());
        return (visualArea / pageArea) >= DOMINANT_VISUAL_MIN_PAGE_RATIO;
    }
}