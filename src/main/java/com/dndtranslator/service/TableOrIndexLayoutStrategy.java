package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;

import java.util.List;

public class TableOrIndexLayoutStrategy extends BasePageLayoutStrategy {

    public TableOrIndexLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        super(pageLayoutBuilder);
    }

    @Override
    public void renderPage(PageRenderContext context) {
        PageMeta meta = context.getPageMeta();
        float margin = Math.max(meta.getLeftMargin(), 28f);

        // Para tablas/indices evitamos fragmentar demasiado para conservar lineas cortas y numeracion.
        LayoutBox singleFlowBox = new LayoutBox(
                margin,
                margin,
                Math.max(1f, meta.getWidth() - (margin * 2f)),
                Math.max(1f, meta.getHeight() - (margin * 2f))
        );

        context.setPageLayout(new PageLayout(List.of(singleFlowBox), toBlockedRegions(context.getPageImages())));
    }
}

