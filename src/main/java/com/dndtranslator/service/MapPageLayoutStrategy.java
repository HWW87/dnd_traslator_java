package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MapPageLayoutStrategy extends BasePageLayoutStrategy {

    private static final float MIN_BOX_WIDTH = 90f;
    private static final float MIN_BOX_HEIGHT = 28f;

    public MapPageLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        super(pageLayoutBuilder);
    }

    @Override
    public void renderPage(PageRenderContext context) {
        PageMeta meta = context.getPageMeta();
        List<BlockedRegion> blockedRegions = toBlockedRegions(context.getPageImages());

        if (blockedRegions.isEmpty()) {
            context.setPageLayout(buildDefaultLayout(meta, context.getPageImages()));
            return;
        }

        BlockedRegion mainVisual = blockedRegions.stream()
                .max(Comparator.comparing(region -> region.width() * region.height()))
                .orElse(null);

        if (mainVisual == null) {
            context.setPageLayout(buildDefaultLayout(meta, context.getPageImages()));
            return;
        }

        StrategyMargins margins = resolveMargins(meta, MAP_MIN_MARGIN, MAP_MIN_MARGIN);

        float pageWidth = meta.getWidth();
        float pageHeight = meta.getHeight();

        float usableLeft = margins.left();
        float usableRight = pageWidth - margins.right();
        float usableBottom = margins.bottom();
        float usableTop = pageHeight - margins.top();

        List<LayoutBox> mapAwareBoxes = new ArrayList<>();

        boolean visualIsWide = mainVisual.width() >= mainVisual.height();

        if (visualIsWide) {
            addIfValid(mapAwareBoxes, new LayoutBox(
                    usableLeft,
                    usableBottom,
                    usableRight - usableLeft,
                    Math.max(0f, mainVisual.y() - usableBottom - VISUAL_REGION_PADDING)
            ));

            addIfValid(mapAwareBoxes, new LayoutBox(
                    usableLeft,
                    mainVisual.top() + VISUAL_REGION_PADDING,
                    usableRight - usableLeft,
                    Math.max(0f, usableTop - (mainVisual.top() + VISUAL_REGION_PADDING))
            ));

            addIfValid(mapAwareBoxes, new LayoutBox(
                    usableLeft,
                    mainVisual.y(),
                    Math.max(0f, mainVisual.x() - usableLeft - VISUAL_REGION_PADDING),
                    mainVisual.height()
            ));

            addIfValid(mapAwareBoxes, new LayoutBox(
                    mainVisual.right() + VISUAL_REGION_PADDING,
                    mainVisual.y(),
                    Math.max(0f, usableRight - (mainVisual.right() + VISUAL_REGION_PADDING)),
                    mainVisual.height()
            ));
        } else {
            addIfValid(mapAwareBoxes, new LayoutBox(
                    usableLeft,
                    mainVisual.y(),
                    Math.max(0f, mainVisual.x() - usableLeft - VISUAL_REGION_PADDING),
                    mainVisual.height()
            ));

            addIfValid(mapAwareBoxes, new LayoutBox(
                    mainVisual.right() + VISUAL_REGION_PADDING,
                    mainVisual.y(),
                    Math.max(0f, usableRight - (mainVisual.right() + VISUAL_REGION_PADDING)),
                    mainVisual.height()
            ));

            addIfValid(mapAwareBoxes, new LayoutBox(
                    usableLeft,
                    usableBottom,
                    usableRight - usableLeft,
                    Math.max(0f, mainVisual.y() - usableBottom - VISUAL_REGION_PADDING)
            ));

            addIfValid(mapAwareBoxes, new LayoutBox(
                    usableLeft,
                    mainVisual.top() + VISUAL_REGION_PADDING,
                    usableRight - usableLeft,
                    Math.max(0f, usableTop - (mainVisual.top() + VISUAL_REGION_PADDING))
            ));
        }

        if (mapAwareBoxes.isEmpty()) {
            context.setPageLayout(buildDefaultLayout(meta, context.getPageImages()));
            return;
        }

        context.setPageLayout(new PageLayout(sortTopDownLeftRight(mapAwareBoxes), blockedRegions));
    }

    private void addIfValid(List<LayoutBox> boxes, LayoutBox box) {
        if (box.width() >= MIN_BOX_WIDTH && box.height() >= MIN_BOX_HEIGHT) {
            boxes.add(box);
        }
    }
}