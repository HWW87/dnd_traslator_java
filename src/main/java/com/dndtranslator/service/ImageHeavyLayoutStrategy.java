package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;

import java.util.Comparator;
import java.util.List;

public class ImageHeavyLayoutStrategy extends BasePageLayoutStrategy {

    public ImageHeavyLayoutStrategy(PageLayoutBuilder pageLayoutBuilder) {
        super(pageLayoutBuilder);
    }

    @Override
    public void renderPage(PageRenderContext context) {
        PageMeta meta = context.getPageMeta();
        PageLayout baseLayout = buildDefaultLayout(meta, context.getPageImages());

        // Conservador: en paginas muy visuales reducimos la cantidad de cajas de texto.
        List<LayoutBox> selected = baseLayout.textBoxes().stream()
                .sorted(Comparator.comparing((LayoutBox box) -> box.width() * box.height()).reversed())
                .limit(2)
                .toList();

        if (selected.isEmpty()) {
            StrategyMargins margins = resolveMargins(meta, IMAGE_HEAVY_MIN_MARGIN, IMAGE_HEAVY_MIN_MARGIN);
            selected = List.of(new LayoutBox(
                    margins.left(),
                    margins.bottom(),
                    Math.max(1f, meta.getWidth() - margins.left() - margins.right()),
                    Math.max(1f, meta.getHeight() - margins.top() - margins.bottom())
            ));
        } else {
            selected = sortTopDownLeftRight(selected);
        }

        context.setPageLayout(new PageLayout(selected, baseLayout.blockedRegions()));
    }
}

