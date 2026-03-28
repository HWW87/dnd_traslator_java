package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

abstract class BasePageLayoutStrategy implements PageLayoutStrategy {

    protected static final float DEFAULT_MIN_MARGIN = 24f;
    protected static final float MAP_MIN_MARGIN = 24f;
    protected static final float TABLE_INDEX_MIN_MARGIN = 28f;
    protected static final float IMAGE_HEAVY_MIN_MARGIN = 30f;
    protected static final float TITLE_COVER_MIN_MARGIN = 36f;
    protected static final float VISUAL_REGION_PADDING = 6f;

    protected final PageLayoutBuilder pageLayoutBuilder;

    protected BasePageLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        this.pageLayoutBuilder = pageLayoutBuilder;
    }

    protected static final class StrategyMargins {
        private final float left;
        private final float right;
        private final float top;
        private final float bottom;

        private StrategyMargins(float left, float right, float top, float bottom) {
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }

        float left() {
            return left;
        }

        float right() {
            return right;
        }

        float top() {
            return top;
        }

        float bottom() {
            return bottom;
        }
    }

    protected PageLayout buildDefaultLayout(PageMeta meta, List<PdfImagePlacement> images) {
        StrategyMargins margins = resolveMargins(meta, DEFAULT_MIN_MARGIN, DEFAULT_MIN_MARGIN);
        List<BlockedRegion> blockedRegions = toBlockedRegions(images);
        return pageLayoutBuilder.build(
                meta.getWidth(),
                meta.getHeight(),
                margins.left(),
                margins.right(),
                margins.top(),
                margins.bottom(),
                blockedRegions
        );
    }

    protected final StrategyMargins resolveMargins(PageMeta meta, float minHorizontalMargin, float minVerticalMargin) {
        float left = Math.max(meta.getLeftMargin(), minHorizontalMargin);
        float right = left;
        float top = Math.max(meta.getTopMargin(), minVerticalMargin);
        float bottom = top;
        return new StrategyMargins(left, right, top, bottom);
    }

    protected final List<BlockedRegion> toBlockedRegions(List<PdfImagePlacement> images) {
        List<BlockedRegion> blockedRegions = new ArrayList<>();
        if (images == null) {
            return blockedRegions;
        }

        for (PdfImagePlacement imagePlacement : images) {
            if (imagePlacement == null || !imagePlacement.isRenderable()) {
                continue;
            }
            if (imagePlacement.width() <= 0f || imagePlacement.height() <= 0f) {
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

    protected final List<LayoutBox> sortTopDownLeftRight(List<LayoutBox> boxes) {
        return boxes.stream()
                .sorted(Comparator.comparing(LayoutBox::top).reversed().thenComparing(LayoutBox::x))
                .toList();
    }
}