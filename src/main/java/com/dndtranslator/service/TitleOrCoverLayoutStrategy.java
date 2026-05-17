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
        float boxHeight = resolveCoverBoxHeight(meta, analysisData, context);

        // Portada/titulo: mantenemos visual principal y render de texto minimo.
        LayoutBox bottomBox = new LayoutBox(
                margins.left(),
                margins.bottom(),
                Math.max(1f, meta.getWidth() - margins.left() - margins.right()),
                Math.max(1f, boxHeight)
        );

        context.setPageLayout(new PageLayout(List.of(bottomBox), toBlockedRegions(context.getPageImages())));
    }

    private float resolveCoverBoxHeight(PageMeta meta, PageAnalysisData analysisData, PageRenderContext context) {
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

        if (shouldUseStrongSuppression(analysisData, context)) {
            ratio = Math.min(ratio, 0.10f);
        }

        float minHeight = shouldUseStrongSuppression(analysisData, context) ? 48f : 60f;
        float maxHeight = shouldUseStrongSuppression(analysisData, context) ? 120f : 220f;
        return Math.min(maxHeight, Math.max(minHeight, meta.getHeight() * ratio));
    }

    private boolean isUltraVisualCover(PageAnalysisData analysisData) {
        return analysisData.hasLargeImage()
                && analysisData.hasVeryLowTextDensity()
                && analysisData.estimatedImageAreaRatio() >= 0.55f
                && analysisData.wordCount() <= 24;
    }

    private boolean shouldUseStrongSuppression(PageAnalysisData analysisData, PageRenderContext context) {
        if (analysisData == null) {
            return context != null && context.hasImages() && !context.hasParagraphs();
        }

        boolean ultraVisual = isUltraVisualCover(analysisData)
                || (analysisData.estimatedImageAreaRatio() >= 0.60f && analysisData.wordCount() <= 20);

        int paragraphCount = context == null ? 0 : context.getParagraphs().size();
        boolean minimalTextContext = paragraphCount <= 1;

        return ultraVisual && minimalTextContext;
    }
}

