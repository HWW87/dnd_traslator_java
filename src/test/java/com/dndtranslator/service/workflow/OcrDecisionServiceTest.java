package com.dndtranslator.service.workflow;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OcrDecisionServiceTest {

    private final OcrDecisionService service = new OcrDecisionService();

    @Test
    void cleanTextDoesNotTriggerFallback() {
        List<Paragraph> paragraphs = List.of(paragraph(repeatWord("adventure", 20)));
        Map<Integer, PageMeta> layout = onePageLayout();

        boolean fallback = service.shouldUseOcrFallback(paragraphs, layout);

        assertFalse(fallback);
    }

    @Test
    void noisyTextTriggersFallback() {
        String noisy = repeatWord("@@@@", 40) + " " + repeatWord("cleanword", 20);
        List<Paragraph> paragraphs = List.of(paragraph(noisy));

        boolean fallback = service.shouldUseOcrFallback(paragraphs, onePageLayout());

        assertTrue(fallback);
    }

    @Test
    void suspiciousCharactersTriggerFallback() {
        String suspicious = repeatWord("?", 20) + " " + repeatWord("normaltext", 20);
        List<Paragraph> paragraphs = List.of(paragraph(suspicious));

        boolean fallback = service.shouldUseOcrFallback(paragraphs, onePageLayout());

        assertTrue(fallback);
    }

    @Test
    void emptyTextTriggersFallback() {
        boolean fallback = service.shouldUseOcrFallback(List.of(), Map.of());
        assertTrue(fallback);
    }

    @Test
    void borderlineTextAtThresholdDoesNotTriggerFallback() {
        String text = "a".repeat(140);
        List<Paragraph> paragraphs = List.of(paragraph(text));

        boolean fallback = service.shouldUseOcrFallback(paragraphs, onePageLayout());

        assertFalse(fallback);
    }

    private static Paragraph paragraph(String text) {
        return new Paragraph(text, 1, 100, 100, "Font", 10);
    }

    private static Map<Integer, PageMeta> onePageLayout() {
        return Map.of(1, new PageMeta(595, 842, 50, 50, 1, "Font", 10));
    }

    private static String repeatWord(String word, int times) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < times; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(word);
        }
        return sb.toString();
    }
}
