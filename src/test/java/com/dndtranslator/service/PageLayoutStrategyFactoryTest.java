package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PageLayoutStrategyFactoryTest {

    private final PageLayoutStrategyFactory factory = new PageLayoutStrategyFactory(new PageLayoutBuilder());

    @Test
    void returnsTextHeavyStrategy() {
        assertInstanceOf(TextHeavyLayoutStrategy.class, factory.getStrategy(PageType.TEXT_HEAVY));
    }

    @Test
    void returnsImageHeavyStrategy() {
        assertInstanceOf(ImageHeavyLayoutStrategy.class, factory.getStrategy(PageType.IMAGE_HEAVY));
    }

    @Test
    void returnsMapStrategy() {
        assertInstanceOf(MapPageLayoutStrategy.class, factory.getStrategy(PageType.MAP_PAGE));
    }

    @Test
    void returnsTableOrIndexStrategy() {
        assertInstanceOf(TableOrIndexLayoutStrategy.class, factory.getStrategy(PageType.TABLE_OR_INDEX));
    }

    @Test
    void returnsTitleOrCoverStrategy() {
        assertInstanceOf(TitleOrCoverLayoutStrategy.class, factory.getStrategy(PageType.TITLE_OR_COVER));
    }

    @Test
    void returnsMixedLayoutStrategy() {
        assertInstanceOf(MixedLayoutStrategy.class, factory.getStrategy(PageType.MIXED_LAYOUT));
    }

    @Test
    void returnsUnknownStrategyWhenExplicitUnknown() {
        assertInstanceOf(UnknownLayoutStrategy.class, factory.getStrategy(PageType.UNKNOWN));
    }

    @Test
    void fallsBackToUnknownStrategy() {
        assertInstanceOf(UnknownLayoutStrategy.class, factory.getStrategy(null));
    }
}

