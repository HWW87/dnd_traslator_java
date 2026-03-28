package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PageTextRenderer {

    private static final float MIN_MARGIN = 24f;

    private final Map<Integer, Boolean> glyphSupportCache = new HashMap<>();
    private final TextLayoutEngine textLayoutEngine;

    public PageTextRenderer(TextLayoutEngine textLayoutEngine) {
        this.textLayoutEngine = textLayoutEngine;
    }

    public List<String> writeParagraphs(
            PDPageContentStream contentStream,
            PDType0Font font,
            PageMeta pageMeta,
            List<Paragraph> paragraphs,
            PageLayout pageLayout,
            List<String> carryOverTexts
    ) throws IOException {
        List<String> overflowTexts = new ArrayList<>();

        if (carryOverTexts != null) {
            for (String carryOver : carryOverTexts) {
                if (carryOver == null || carryOver.isBlank()) {
                    continue;
                }
                TextLayoutEngine.RenderResult rendered = textLayoutEngine.renderText(
                        contentStream,
                        pageLayout.textBoxes(),
                        sanitizeForFont(carryOver, font),
                        font,
                        Math.max(pageMeta.getLeftMargin(), MIN_MARGIN),
                        pageMeta.getHeight() - Math.max(pageMeta.getTopMargin(), MIN_MARGIN)
                );
                if (!rendered.remainingText().isBlank()) {
                    overflowTexts.add(rendered.remainingText());
                }
            }
        }

        if (paragraphs == null) {
            return overflowTexts;
        }

        for (Paragraph paragraph : paragraphs) {
            String text = paragraph.getTranslatedText();
            if (text == null || text.isBlank()) {
                continue;
            }

            float x = paragraph.getX();
            float y = paragraph.getY();
            if (y < 50) {
                y = 60;
            }

            String safeText = sanitizeForFont(text, font);
            if (safeText.isBlank()) {
                continue;
            }

            TextLayoutEngine.RenderResult rendered = textLayoutEngine.renderText(
                    contentStream,
                    pageLayout.textBoxes(),
                    safeText,
                    font,
                    x,
                    y
            );

            if (!rendered.remainingText().isBlank()) {
                overflowTexts.add(rendered.remainingText());
            }
        }

        return overflowTexts;
    }

    String sanitizeForFont(String text, PDType0Font font) {
        StringBuilder safe = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String character = new String(Character.toChars(codePoint));
            if (isGlyphSupported(font, codePoint)) {
                safe.append(character);
            } else {
                safe.append('?');
            }
            i += Character.charCount(codePoint);
        }
        return safe.toString();
    }

    private boolean isGlyphSupported(PDType0Font font, int codePoint) {
        Boolean cached = glyphSupportCache.get(codePoint);
        if (cached != null) {
            return cached;
        }

        boolean supported;
        try {
            font.encode(new String(Character.toChars(codePoint)));
            supported = true;
        } catch (Exception e) {
            supported = false;
        }

        glyphSupportCache.put(codePoint, supported);
        return supported;
    }
}

