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
        PageAnalysisData analysisData = context.getAnalysisData();
        StrategyMargins margins = resolveMargins(meta, TITLE_COVER_MIN_MARGIN, TITLE_COVER_MIN_MARGIN);
        float boxHeight = resolveCoverBoxHeight(meta, analysisData);

        // Portada/titulo: mantenemos visual principal y render de texto minimo.
        LayoutBox bottomBox = new LayoutBox(
                margins.left(),
                margins.bottom(),
                Math.max(1f, meta.getWidth() - margins.left() - margins.right()),
                Math.max(1f, boxHeight)
        );

        context.setPageLayout(new PageLayout(List.of(bottomBox), toBlockedRegions(context.getPageImages())));
    }

    private float resolveCoverBoxHeight(PageMeta meta, PageAnalysisData analysisData) {
        float ratio = 0.22f;
        if (analysisData != null) {
            if (isUltraVisualCover(analysisData)) {
                ratio = 0.13f;
            } else if (analysisData.hasLargeImage() || analysisData.hasVeryLowTextDensity()) {
                ratio = 0.16f;
            } else if (analysisData.wordCount() > 80) {
                ratio = 0.26f;
            }
        }
        return Math.min(220f, Math.max(60f, meta.getHeight() * ratio));
    }

    private boolean isUltraVisualCover(PageAnalysisData analysisData) {
        return analysisData.hasLargeImage()
                && analysisData.hasVeryLowTextDensity()
                && analysisData.estimatedImageAreaRatio() >= 0.55f
                && analysisData.wordCount() <= 24;
    }
}

