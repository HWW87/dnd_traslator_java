package com.dndtranslator.service.workflow;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;

import java.util.List;
import java.util.Map;

public class OcrDecisionService {

    private static final int MIN_TEXT_CHARS_PER_PAGE = 140;
    private static final double MAX_NOISY_RATIO_FOR_EMBEDDED = 0.28d;
    private static final double MAX_SUSPICIOUS_RATIO_FOR_EMBEDDED = 0.035d;
    private static final int MIN_SUSPICIOUS_CHARS_PER_PAGE = 10;

    public boolean shouldUseOcrFallback(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return true;
        }

        int pages = Math.max(1, layoutInfo.isEmpty()
                ? paragraphs.stream().mapToInt(Paragraph::getPage).max().orElse(1)
                : layoutInfo.size());

        int totalChars = paragraphs.stream().mapToInt(p -> p.getFullText().length()).sum();
        int expectedMinChars = pages * MIN_TEXT_CHARS_PER_PAGE;
        if (totalChars < expectedMinChars) {
            return true;
        }

        double noisyRatio = computeNoisyRatio(paragraphs);
        if (noisyRatio > MAX_NOISY_RATIO_FOR_EMBEDDED) {
            return true;
        }

        double suspiciousRatio = computeSuspiciousRatio(paragraphs);
        if (suspiciousRatio > MAX_SUSPICIOUS_RATIO_FOR_EMBEDDED) {
            return true;
        }

        int suspiciousChars = countSuspiciousChars(paragraphs);
        return suspiciousChars >= pages * MIN_SUSPICIOUS_CHARS_PER_PAGE;
    }

    private double computeNoisyRatio(List<Paragraph> paragraphs) {
        long totalChars = 0;
        long noisyChars = 0;

        for (Paragraph paragraph : paragraphs) {
            String text = paragraph.getFullText();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isWhitespace(c)) {
                    continue;
                }
                totalChars++;
                if (!Character.isLetterOrDigit(c) && ",.;:!?()[]{}'\"-_/".indexOf(c) < 0) {
                    noisyChars++;
                }
            }
        }

        if (totalChars == 0) {
            return 1d;
        }
        return (double) noisyChars / (double) totalChars;
    }

    private double computeSuspiciousRatio(List<Paragraph> paragraphs) {
        long totalChars = 0;
        long suspiciousChars = 0;

        for (Paragraph paragraph : paragraphs) {
            String text = paragraph.getFullText();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isWhitespace(c)) {
                    continue;
                }
                totalChars++;

                if (c == '?' || c == '\u000B' || c == '\u000C') {
                    suspiciousChars++;
                    continue;
                }

                if (Character.isISOControl(c)) {
                    suspiciousChars++;
                    continue;
                }

                boolean alnum = Character.isLetterOrDigit(c);
                boolean basicPunct = ",.;:!?()[]{}'\"-_/".indexOf(c) >= 0;
                if (!alnum && !basicPunct) {
                    suspiciousChars++;
                }
            }
        }

        if (totalChars == 0) {
            return 1d;
        }
        return (double) suspiciousChars / (double) totalChars;
    }

    private int countSuspiciousChars(List<Paragraph> paragraphs) {
        int suspiciousChars = 0;
        for (Paragraph paragraph : paragraphs) {
            String text = paragraph.getFullText();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isWhitespace(c)) {
                    continue;
                }
                if (c == '?' || c == '\u000B' || c == '\u000C' || Character.isISOControl(c)) {
                    suspiciousChars++;
                    continue;
                }
                boolean alnum = Character.isLetterOrDigit(c);
                boolean basicPunct = ",.;:!?()[]{}'\"-_/".indexOf(c) >= 0;
                if (!alnum && !basicPunct) {
                    suspiciousChars++;
                }
            }
        }
        return suspiciousChars;
    }
}

