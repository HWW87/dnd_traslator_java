package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;

import java.util.List;

public class TitleOrCoverLayoutStrategy extends BasePageLayoutStrategy {

    public TitleOrCoverLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        super(pageLayoutBuilder);
    }

    @Override
    public void renderPage(PageRenderContext context) {
        PageMeta meta = context.getPageMeta();
        StrategyMargins margins = resolveMargins(meta, TITLE_COVER_MIN_MARGIN, TITLE_COVER_MIN_MARGIN);
        float boxHeight = Math.min(180f, Math.max(60f, meta.getHeight() * 0.22f));

        // Portada/titulo: mantenemos visual principal y render de texto minimo.
        LayoutBox bottomBox = new LayoutBox(
                margins.left(),
                margins.bottom(),
                Math.max(1f, meta.getWidth() - margins.left() - margins.right()),
                Math.max(1f, boxHeight)
        );

        context.setPageLayout(new PageLayout(List.of(bottomBox), toBlockedRegions(context.getPageImages())));
    }
}

