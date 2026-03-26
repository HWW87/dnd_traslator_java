package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

abstract class BasePageLayoutStrategy implements PageLayoutStrategy {

    protected final PageLayoutBuilder pageLayoutBuilder;

    protected BasePageLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        this.pageLayoutBuilder = pageLayoutBuilder;
    }

    protected PageLayout buildDefaultLayout(PageMeta meta, List<PdfImagePlacement> images) {
        float margin = Math.max(meta.getLeftMargin(), 24f);
        List<BlockedRegion> blockedRegions = toBlockedRegions(images);
        return pageLayoutBuilder.build(meta.getWidth(), meta.getHeight(), margin, blockedRegions);
    }

    protected List<BlockedRegion> toBlockedRegions(List<PdfImagePlacement> images) {
        List<BlockedRegion> blockedRegions = new ArrayList<>();
        if (images == null) {
            return blockedRegions;
        }
        for (PdfImagePlacement imagePlacement : images) {
            if (imagePlacement == null || !imagePlacement.isRenderable()) {
                continue;
            }
            blockedRegions.add(new BlockedRegion(
                    imagePlacement.x(),
                    imagePlacement.y(),
                    imagePlacement.width(),
                    imagePlacement.height()
            ));
        }
        return blockedRegions;
    }

    protected List<LayoutBox> sortTopDownLeftRight(List<LayoutBox> boxes) {
        return boxes.stream()
                .sorted(Comparator.comparing(LayoutBox::top).reversed().thenComparing(LayoutBox::x))
                .toList();
    }
}

