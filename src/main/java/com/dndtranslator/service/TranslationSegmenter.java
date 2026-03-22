package com.dndtranslator.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TranslationSegmenter {

    private static final int DEFAULT_MAX_WORDS = 1000;

    private final int defaultMaxWords;

    public TranslationSegmenter() {
        this(DEFAULT_MAX_WORDS);
    }

    public TranslationSegmenter(int defaultMaxWords) {
        this.defaultMaxWords = Math.max(1, defaultMaxWords);
    }

    public List<String> segment(String text) {
        return segment(text, defaultMaxWords);
    }

    public List<String> segment(String text, int maxWords) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        int normalizedMaxWords = Math.max(1, maxWords);
        String[] words = text.trim().split("\\s+");
        List<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int wordCount = 0;

        for (String word : words) {
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
            wordCount++;

            if (wordCount >= normalizedMaxWords) {
                segments.add(current.toString());
                current.setLength(0);
                wordCount = 0;
            }
        }

        if (current.length() > 0) {
            segments.add(current.toString());
        }

        return segments;
    }
}

