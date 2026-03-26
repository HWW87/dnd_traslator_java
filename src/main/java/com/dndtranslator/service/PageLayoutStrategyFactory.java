package com.dndtranslator.service;

import java.util.EnumMap;
import java.util.Map;

public class PageLayoutStrategyFactory {

    private final Map<PageType, PageLayoutStrategy> strategies;
    private final PageLayoutStrategy fallbackStrategy;

    public PageLayoutStrategyFactory(PageLayoutBuilder pageLayoutBuilder) {
        this.strategies = new EnumMap<>(PageType.class);

        strategies.put(PageType.TEXT_HEAVY, new TextHeavyLayoutStrategy(pageLayoutBuilder));
        strategies.put(PageType.IMAGE_HEAVY, new ImageHeavyLayoutStrategy(pageLayoutBuilder));
        strategies.put(PageType.MAP_PAGE, new MapPageLayoutStrategy(pageLayoutBuilder));
        strategies.put(PageType.TABLE_OR_INDEX, new TableOrIndexLayoutStrategy(pageLayoutBuilder));
        strategies.put(PageType.TITLE_OR_COVER, new TitleOrCoverLayoutStrategy(pageLayoutBuilder));
        strategies.put(PageType.MIXED_LAYOUT, new MixedLayoutStrategy(pageLayoutBuilder));
        strategies.put(PageType.UNKNOWN, new UnknownLayoutStrategy(pageLayoutBuilder));

        this.fallbackStrategy = strategies.get(PageType.UNKNOWN);
    }

    public PageLayoutStrategy getStrategy(PageType pageType) {
        if (pageType == null) {
            return fallbackStrategy;
        }
        return strategies.getOrDefault(pageType, fallbackStrategy);
    }
}

