package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageLayoutBuilderTest {

    @Test
    void createsTextBoxesOutsideBlockedRegions() {
        PageLayoutBuilder builder = new PageLayoutBuilder();

        BlockedRegion blocked = new BlockedRegion(120f, 180f, 120f, 100f);
        PageLayout layout = builder.build(400f, 500f, 24f, List.of(blocked));

        assertFalse(layout.textBoxes().isEmpty());
        for (LayoutBox box : layout.textBoxes()) {
            assertFalse(blocked.intersects(box));
        }
    }

    @Test
    void keepsFallbackTextAreaWhenBlockedRegionsConsumeMostSpace() {
        PageLayoutBuilder builder = new PageLayoutBuilder();

        List<BlockedRegion> blocked = List.of(
                new BlockedRegion(20f, 20f, 360f, 460f)
        );

        PageLayout layout = builder.build(400f, 500f, 24f, blocked);

        assertTrue(layout.textBoxes().size() >= 1);
    }

    @Test
    void supportsExplicitMarginsPerSide() {
        PageLayoutBuilder builder = new PageLayoutBuilder();

        PageLayout layout = builder.build(400f, 500f, 40f, 30f, 70f, 20f, List.of());

        LayoutBox box = layout.textBoxes().get(0);
        assertEquals(40f, box.x());
        assertEquals(20f, box.y());
        assertEquals(330f, box.width());
        assertEquals(410f, box.height());
    }
}

