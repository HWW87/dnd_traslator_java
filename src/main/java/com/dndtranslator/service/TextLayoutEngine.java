package com.dndtranslator.service;

import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TextLayoutEngine {

    private static final float DEFAULT_LEADING_FACTOR = 1.2f;
    private static final float DEFAULT_START_FONT_SIZE = 11f;
    private static final float DEFAULT_MIN_FONT_SIZE = 8f;

    public List<String> wrapText(String text, PDFont font, float fontSize, float maxWidth) throws IOException {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }
        if (maxWidth <= 0f) {
            return List.of(text.trim());
        }

        List<String> lines = new ArrayList<>();
        String[] words = text.replace("\t", " ").trim().split("\\s+");
        StringBuilder current = new StringBuilder();

        for (String word : words) {
            if (current.isEmpty()) {
                if (measureTextWidth(word, font, fontSize) > maxWidth) {
                    lines.addAll(splitLongTokenByWidth(word, font, fontSize, maxWidth));
                } else {
                    current.append(word);
                }
                continue;
            }

            String candidate = current + " " + word;
            if (measureTextWidth(candidate, font, fontSize) <= maxWidth) {
                current.append(" ").append(word);
            } else {
                lines.add(current.toString());
                if (measureTextWidth(word, font, fontSize) > maxWidth) {
                    lines.addAll(splitLongTokenByWidth(word, font, fontSize, maxWidth));
                    current.setLength(0);
                } else {
                    current = new StringBuilder(word);
                }
            }
        }

        if (!current.isEmpty()) {
            lines.add(current.toString());
        }

        return lines;
    }

    private List<String> splitLongTokenByWidth(String token, PDFont font, float fontSize, float maxWidth) throws IOException {
        if (token == null || token.isBlank()) {
            return Collections.emptyList();
        }

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (int offset = 0; offset < token.length(); ) {
            int codePoint = token.codePointAt(offset);
            String character = new String(Character.toChars(codePoint));

            if (current.isEmpty()) {
                current.append(character);
            } else {
                String candidate = current + character;
                if (measureTextWidth(candidate, font, fontSize) <= maxWidth) {
                    current.append(character);
                } else {
                    chunks.add(current.toString());
                    current.setLength(0);
                    current.append(character);
                }
            }

            offset += Character.charCount(codePoint);
        }

        if (!current.isEmpty()) {
            chunks.add(current.toString());
        }

        return chunks;
    }

    public float measureTextWidth(String text, PDFont font, float fontSize) throws IOException {
        if (text == null || text.isBlank()) {
            return 0f;
        }
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    public RenderResult renderText(
            PDPageContentStream stream,
            List<LayoutBox> boxes,
            String text,
            PDType0Font font,
            float preferredX,
            float preferredY
    ) throws IOException {
        if (text == null || text.isBlank()) {
            return new RenderResult("", false, DEFAULT_START_FONT_SIZE);
        }
        if (boxes == null || boxes.isEmpty()) {
            return new RenderResult(text, false, DEFAULT_MIN_FONT_SIZE);
        }

        int startIndex = selectStartingBoxIndex(boxes, preferredX, preferredY);
        String remainingText = text.trim();
        boolean wroteAny = false;
        float lastFontSize = DEFAULT_START_FONT_SIZE;

        for (int i = startIndex; i < boxes.size() && !remainingText.isBlank(); i++) {
            LayoutBox box = boxes.get(i);
            BoxRenderResult boxResult = renderTextInBox(stream, box, remainingText, font);
            remainingText = boxResult.remainingText();
            wroteAny = wroteAny || boxResult.wroteAny();
            lastFontSize = boxResult.usedFontSize();
        }

        return new RenderResult(remainingText, wroteAny, lastFontSize);
    }

    public BoxRenderResult renderTextInBox(PDPageContentStream stream, LayoutBox box, String text, PDType0Font font) throws IOException {
        if (text == null || text.isBlank()) {
            return new BoxRenderResult("", false, DEFAULT_START_FONT_SIZE);
        }

        float fontSize = chooseFontSize(text, box, font);
        List<String> wrapped = wrapText(text, font, fontSize, box.width());

        int maxLines = Math.max(1, (int) Math.floor(box.height() / (DEFAULT_LEADING_FACTOR * fontSize)));
        int linesToRender = Math.min(maxLines, wrapped.size());
        if (linesToRender <= 0) {
            return new BoxRenderResult(text, false, fontSize);
        }

        float leading = DEFAULT_LEADING_FACTOR * fontSize;
        float startY = box.top() - fontSize;

        stream.beginText();
        stream.setFont(font, fontSize);
        stream.newLineAtOffset(box.x(), startY);

        for (int i = 0; i < linesToRender; i++) {
            stream.showText(wrapped.get(i));
            if (i < linesToRender - 1) {
                stream.newLineAtOffset(0, -leading);
            }
        }
        stream.endText();

        if (linesToRender >= wrapped.size()) {
            return new BoxRenderResult("", true, fontSize);
        }

        StringBuilder remaining = new StringBuilder();
        for (int i = linesToRender; i < wrapped.size(); i++) {
            if (!remaining.isEmpty()) {
                remaining.append(' ');
            }
            remaining.append(wrapped.get(i));
        }

        return new BoxRenderResult(remaining.toString(), true, fontSize);
    }

    private float chooseFontSize(String text, LayoutBox box, PDType0Font font) throws IOException {
        for (float size = DEFAULT_START_FONT_SIZE; size >= DEFAULT_MIN_FONT_SIZE; size -= 1f) {
            List<String> wrapped = wrapText(text, font, size, box.width());
            int maxLines = Math.max(1, (int) Math.floor(box.height() / (DEFAULT_LEADING_FACTOR * size)));
            if (wrapped.size() <= maxLines) {
                return size;
            }
        }
        return DEFAULT_MIN_FONT_SIZE;
    }

    private int selectStartingBoxIndex(List<LayoutBox> boxes, float x, float y) {
        for (int i = 0; i < boxes.size(); i++) {
            if (boxes.get(i).contains(x, y)) {
                return i;
            }
        }

        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < boxes.size(); i++) {
            LayoutBox box = boxes.get(i);
            float centerX = box.x() + (box.width() / 2f);
            float centerY = box.y() + (box.height() / 2f);
            double distance = Math.hypot(centerX - x, centerY - y);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    public record RenderResult(String remainingText, boolean wroteAny, float usedFontSize) {
    }

    public record BoxRenderResult(String remainingText, boolean wroteAny, float usedFontSize) {
    }
}

