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
        float margin = Math.max(meta.getLeftMargin(), 36f);
        float boxHeight = Math.min(180f, Math.max(60f, meta.getHeight() * 0.22f));

        // Portada/titulo: mantenemos visual principal y render de texto minimo.
        LayoutBox bottomBox = new LayoutBox(
                margin,
                margin,
                Math.max(1f, meta.getWidth() - (margin * 2f)),
                Math.max(1f, boxHeight)
        );

        context.setPageLayout(new PageLayout(List.of(bottomBox), toBlockedRegions(context.getPageImages())));
    }
}

