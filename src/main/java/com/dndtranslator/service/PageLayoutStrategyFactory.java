package com.dndtranslator.service;

import java.util.EnumMap;
import java.util.Map;

public class PageLayoutStrategyFactory {

    private final Map<PageType, PageLayoutStrategy> strategies;
    private final PageLayoutStrategy fallbackStrategy;

    public PageLayoutStrategyFactory(PageLayoutBuilder pageLayoutBuilder) {
        EnumMap<PageType, PageLayoutStrategy> strategyMap = new EnumMap<>(PageType.class);

        strategyMap.put(PageType.TEXT_HEAVY, new TextHeavyLayoutStrategy(pageLayoutBuilder));
        strategyMap.put(PageType.IMAGE_HEAVY, new ImageHeavyLayoutStrategy(pageLayoutBuilder));
        strategyMap.put(PageType.MAP_PAGE, new MapPageLayoutStrategy(pageLayoutBuilder));
        strategyMap.put(PageType.TABLE_OR_INDEX, new TableOrIndexLayoutStrategy(pageLayoutBuilder));
        strategyMap.put(PageType.TITLE_OR_COVER, new TitleOrCoverLayoutStrategy(pageLayoutBuilder));
        strategyMap.put(PageType.MIXED_LAYOUT, new MixedLayoutStrategy(pageLayoutBuilder));
        strategyMap.put(PageType.UNKNOWN, new UnknownLayoutStrategy(pageLayoutBuilder));

        PageLayoutStrategy unknown = strategyMap.get(PageType.UNKNOWN);
        if (unknown == null) {
            throw new IllegalStateException("Missing fallback strategy for PageType.UNKNOWN");
        }

        this.strategies = strategyMap;
        this.fallbackStrategy = unknown;
    }

    public PageLayoutStrategy getStrategy(PageType pageType) {
        if (pageType == null) {
            return fallbackStrategy;
        }
        return strategies.getOrDefault(pageType, fallbackStrategy);
    }
}