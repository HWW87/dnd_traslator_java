package com.dndtranslator.service;

public class MixedLayoutStrategy extends BasePageLayoutStrategy {

    public MixedLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        super(pageLayoutBuilder);
    }

    @Override
    public void renderPage(PageRenderContext context) {
        // Reutiliza layout con regiones bloqueadas para paginas mixtas.
        context.setPageLayout(buildDefaultLayout(context.getPageMeta(), context.getPageImages()));
    }
}

