package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TableOrIndexLayoutStrategy extends BasePageLayoutStrategy {

    private static final float MIN_MARGIN = 28f;
    private static final float VISUAL_PADDING = 6f;
    private static final float MIN_BOX_WIDTH = 120f;
    private static final float MIN_BOX_HEIGHT = 40f;

    public TableOrIndexLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        super(pageLayoutBuilder);
    }

    @Override
    public void renderPage(PageRenderContext context) {
        PageMeta meta = context.getPageMeta();
        float margin = Math.max(meta.getLeftMargin(), MIN_MARGIN);

        List<BlockedRegion> blockedRegions = toBlockedRegions(context.getPageImages());
        List<LayoutBox> boxes = new ArrayList<>();

        if (blockedRegions.isEmpty()) {
            boxes.add(buildFullFlowBox(meta, margin));
            context.setPageLayout(new PageLayout(boxes, blockedRegions));
            return;
        }

        BlockedRegion mainVisual = blockedRegions.stream()
                .max(Comparator.comparing(region -> region.width() * region.height()))
                .orElse(null);

        if (mainVisual == null) {
            boxes.add(buildFullFlowBox(meta, margin));
            context.setPageLayout(new PageLayout(boxes, blockedRegions));
            return;
        }

        float pageWidth = meta.getWidth();
        float pageHeight = meta.getHeight();
        float usableWidth = Math.max(1f, pageWidth - (margin * 2f));
        float usableHeight = Math.max(1f, pageHeight - (margin * 2f));

        LayoutBox topBox = new LayoutBox(
                margin,
                margin,
                usableWidth,
                Math.max(0f, mainVisual.y() - margin - VISUAL_PADDING)
        );

        LayoutBox bottomBox = new LayoutBox(
                margin,
                mainVisual.top() + VISUAL_PADDING,
                usableWidth,
                Math.max(0f, (pageHeight - margin) - (mainVisual.top() + VISUAL_PADDING))
        );

        LayoutBox leftBox = new LayoutBox(
                margin,
                mainVisual.y(),
                Math.max(0f, mainVisual.x() - margin - VISUAL_PADDING),
                mainVisual.height()
        );

        LayoutBox rightBox = new LayoutBox(
                mainVisual.right() + VISUAL_PADDING,
                mainVisual.y(),
                Math.max(0f, (pageWidth - margin) - (mainVisual.right() + VISUAL_PADDING)),
                mainVisual.height()
        );

        addIfValid(boxes, topBox);
        addIfValid(boxes, bottomBox);
        addIfValid(boxes, leftBox);
        addIfValid(boxes, rightBox);

        if (boxes.isEmpty()) {
            boxes.add(buildFullFlowBox(meta, margin));
        }

        context.setPageLayout(new PageLayout(sortTopDownLeftRight(boxes), blockedRegions));
    }

    private LayoutBox buildFullFlowBox(PageMeta meta, float margin) {
        return new LayoutBox(
                margin,
                margin,
                Math.max(1f, meta.getWidth() - (margin * 2f)),
                Math.max(1f, meta.getHeight() - (margin * 2f))
        );
    }

    private void addIfValid(List<LayoutBox> boxes, LayoutBox box) {
        if (box.width() >= MIN_BOX_WIDTH && box.height() >= MIN_BOX_HEIGHT) {
            boxes.add(box);
        }
    }
}