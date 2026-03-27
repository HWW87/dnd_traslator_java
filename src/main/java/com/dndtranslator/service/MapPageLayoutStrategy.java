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

        float margin = Math.max(meta.getLeftMargin(), 24f);
        float usableWidth = Math.max(1f, meta.getWidth() - (margin * 2f));
        float usableTop = meta.getHeight() - margin;

        List<LayoutBox> mapAwareBoxes = new ArrayList<>();

        // Zonas tipicas para mapas: leyenda lateral y texto inferior/superior.
        addIfValid(mapAwareBoxes, new LayoutBox(
                margin,
                margin,
                usableWidth,
                Math.max(0f, mainVisual.y() - margin - 6f)
        ));

        addIfValid(mapAwareBoxes, new LayoutBox(
                margin,
                mainVisual.top() + 6f,
                usableWidth,
                Math.max(0f, usableTop - (mainVisual.top() + 6f))
        ));

        addIfValid(mapAwareBoxes, new LayoutBox(
                margin,
                mainVisual.y(),
                Math.max(0f, mainVisual.x() - margin - 6f),
                mainVisual.height()
        ));

        addIfValid(mapAwareBoxes, new LayoutBox(
                mainVisual.right() + 6f,
                mainVisual.y(),
                Math.max(0f, meta.getWidth() - margin - (mainVisual.right() + 6f)),
                mainVisual.height()
        ));

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

