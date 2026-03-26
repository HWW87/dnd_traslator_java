package com.dndtranslator.service;

public class UnknownLayoutStrategy extends BasePageLayoutStrategy {

    public UnknownLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        super(pageLayoutBuilder);
    }

    @Override
    public void renderPage(PageRenderContext context) {
        context.setPageLayout(buildDefaultLayout(context.getPageMeta(), context.getPageImages()));
    }
}

