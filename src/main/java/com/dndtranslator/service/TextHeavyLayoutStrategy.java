package com.dndtranslator.service;

public class TextHeavyLayoutStrategy extends BasePageLayoutStrategy {

    public TextHeavyLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        super(pageLayoutBuilder);
    }

    @Override
    public void renderPage(PageRenderContext context) {
        context.setPageLayout(buildDefaultLayout(context.getPageMeta(), context.getPageImages()));
    }
}

