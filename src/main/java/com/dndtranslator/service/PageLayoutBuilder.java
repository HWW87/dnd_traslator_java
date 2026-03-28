package com.dndtranslator.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PageLayoutBuilder {

    private static final float REGION_PADDING = 6f;
    private static final float MIN_BOX_WIDTH = 48f;
    private static final float MIN_BOX_HEIGHT = 22f;
    private static final float MIN_EFFECTIVE_MARGIN = 12f;

    public PageLayout build(float pageWidth, float pageHeight, float margin, List<BlockedRegion> blockedRegions) {
        return build(pageWidth, pageHeight, margin, margin, margin, margin, blockedRegions);
    }

    public PageLayout build(
            float pageWidth,
            float pageHeight,
            float horizontalMargin,
            float verticalMargin,
            List<BlockedRegion> blockedRegions
    ) {
        return build(
                pageWidth,
                pageHeight,
                horizontalMargin,
                horizontalMargin,
                verticalMargin,
                verticalMargin,
                blockedRegions
        );
    }

    public PageLayout build(
            float pageWidth,
            float pageHeight,
            float leftMargin,
            float rightMargin,
            float topMargin,
            float bottomMargin,
            List<BlockedRegion> blockedRegions
    ) {
        float effectiveLeftMargin = Math.max(MIN_EFFECTIVE_MARGIN, leftMargin);
        float effectiveRightMargin = Math.max(MIN_EFFECTIVE_MARGIN, rightMargin);
        float effectiveTopMargin = Math.max(MIN_EFFECTIVE_MARGIN, topMargin);
        float effectiveBottomMargin = Math.max(MIN_EFFECTIVE_MARGIN, bottomMargin);
        LayoutBox initial = new LayoutBox(
                effectiveLeftMargin,
                effectiveBottomMargin,
                Math.max(1f, pageWidth - effectiveLeftMargin - effectiveRightMargin),
                Math.max(1f, pageHeight - effectiveTopMargin - effectiveBottomMargin)
        );

        List<LayoutBox> boxes = new ArrayList<>();
        boxes.add(initial);

        List<BlockedRegion> normalizedRegions = normalizeBlockedRegions(blockedRegions, pageWidth, pageHeight);
        for (BlockedRegion blockedRegion : normalizedRegions) {
            List<LayoutBox> next = new ArrayList<>();
            for (LayoutBox box : boxes) {
                next.addAll(subtract(box, blockedRegion));
            }
            boxes = pruneBoxes(next);
        }

        if (boxes.isEmpty()) {
            // Fallback defensivo: evita perder texto si toda la pagina queda bloqueada.
            boxes = List.of(initial);
        }

        boxes = boxes.stream()
                .sorted(Comparator.comparing(LayoutBox::top).reversed().thenComparing(LayoutBox::x))
                .toList();

        return new PageLayout(boxes, normalizedRegions);
    }

    private List<BlockedRegion> normalizeBlockedRegions(List<BlockedRegion> blockedRegions, float pageWidth, float pageHeight) {
        List<BlockedRegion> normalized = new ArrayList<>();
        if (blockedRegions == null) {
            return normalized;
        }

        for (BlockedRegion region : blockedRegions) {
            if (region == null || region.width() <= 0f || region.height() <= 0f) {
                continue;
            }
            float x = clamp(region.x() - REGION_PADDING, 0f, pageWidth);
            float y = clamp(region.y() - REGION_PADDING, 0f, pageHeight);
            float right = clamp(region.right() + REGION_PADDING, 0f, pageWidth);
            float top = clamp(region.top() + REGION_PADDING, 0f, pageHeight);
            float width = Math.max(0f, right - x);
            float height = Math.max(0f, top - y);
            if (width > 0f && height > 0f) {
                normalized.add(new BlockedRegion(x, y, width, height));
            }
        }

        return normalized;
    }

    private List<LayoutBox> subtract(LayoutBox box, BlockedRegion region) {
        if (!region.intersects(box)) {
            return List.of(box);
        }

        float bx1 = box.x();
        float bx2 = box.right();
        float by1 = box.y();
        float by2 = box.top();

        float rx1 = Math.max(bx1, region.x());
        float rx2 = Math.min(bx2, region.right());
        float ry1 = Math.max(by1, region.y());
        float ry2 = Math.min(by2, region.top());

        List<LayoutBox> fragments = new ArrayList<>();

        addIfValid(fragments, new LayoutBox(bx1, by1, rx1 - bx1, box.height()));
        addIfValid(fragments, new LayoutBox(rx2, by1, bx2 - rx2, box.height()));
        addIfValid(fragments, new LayoutBox(rx1, by1, rx2 - rx1, ry1 - by1));
        addIfValid(fragments, new LayoutBox(rx1, ry2, rx2 - rx1, by2 - ry2));

        return fragments;
    }

    private List<LayoutBox> pruneBoxes(List<LayoutBox> boxes) {
        List<LayoutBox> pruned = new ArrayList<>();
        for (LayoutBox box : boxes) {
            addIfValid(pruned, box);
        }
        return pruned;
    }

    private void addIfValid(List<LayoutBox> boxes, LayoutBox box) {
        if (box.width() >= MIN_BOX_WIDTH && box.height() >= MIN_BOX_HEIGHT) {
            boxes.add(box);
        }
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}

