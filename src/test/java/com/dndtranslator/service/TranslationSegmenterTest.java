package com.dndtranslator.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TranslationSegmenterTest {

    @Test
    void returnsEmptySegmentsForBlankText() {
        TranslationSegmenter segmenter = new TranslationSegmenter();

        List<String> segments = segmenter.segment("   ");

        assertTrue(segments.isEmpty());
    }

    @Test
    void splitsTextByConfiguredMaxWords() {
        TranslationSegmenter segmenter = new TranslationSegmenter();

        List<String> segments = segmenter.segment("one two three four five six seven", 3);

        assertEquals(3, segments.size());
        assertEquals("one two three", segments.get(0));
        assertEquals("four five six", segments.get(1));
        assertEquals("seven", segments.get(2));
    }
}

