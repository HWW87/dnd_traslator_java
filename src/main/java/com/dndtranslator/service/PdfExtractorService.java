package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PdfExtractorService extends PDFTextStripper {

    private final List<Paragraph> paragraphs = new ArrayList<>();
    private final Map<Integer, PageMeta> layoutInfo = new HashMap<>();

    private int currentPage = 1;
    private float currentPageWidth = PDRectangle.LETTER.getWidth();
    private int currentColumns = 1;
    private float currentSplitX = Float.NaN;
    private final Map<String, Integer> fontCount = new HashMap<>();
    private final List<Float> fontSizes = new ArrayList<>();
    private final List<Float> currentPageXs = new ArrayList<>();

    public PdfExtractorService() throws IOException {
        setSortByPosition(true);
    }

    public List<Paragraph> extractParagraphs(String pdfPath) throws IOException {
        paragraphs.clear();
        layoutInfo.clear();

        try (PDDocument document = PDDocument.load(new java.io.File(pdfPath))) {
            writeText(document, new java.io.StringWriter());
        }

        paragraphs.sort(
                Comparator.comparingInt(Paragraph::getPage)
                        .thenComparingInt(this::columnIndexForParagraph)
                        .thenComparing(Paragraph::getY)
                        .thenComparing(Paragraph::getX)
        );
        return paragraphs;
    }

    public Map<Integer, PageMeta> getLayoutInfo() {
        return layoutInfo;
    }

    @Override
    protected void startPage(org.apache.pdfbox.pdmodel.PDPage page) throws IOException {
        currentPage = getCurrentPageNo();
        PDRectangle mediaBox = page.getMediaBox();
        currentPageWidth = mediaBox.getWidth();
        currentColumns = 1;
        currentSplitX = Float.NaN;
        fontCount.clear();
        fontSizes.clear();
        currentPageXs.clear();
        super.startPage(page);
    }

    @Override
    protected void endPage(org.apache.pdfbox.pdmodel.PDPage page) throws IOException {
        float avgFont = (float) fontSizes.stream().mapToDouble(f -> f).average().orElse(12.0);
        String mainFont = fontCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("Unknown");

        ColumnLayout layout = detectColumnLayout(currentPageWidth, currentPageXs);
        currentColumns = layout.columns();
        currentSplitX = layout.splitX();

        PDRectangle mediaBox = page.getMediaBox();
        float width = mediaBox.getWidth();
        float height = mediaBox.getHeight();

        layoutInfo.put(currentPage,
                new PageMeta(width, height, 50, 50, currentColumns, mainFont, avgFont, currentSplitX));
        super.endPage(page);
    }

    @Override
    protected void writeString(String text, List<TextPosition> textPositions) {
        if (text == null || text.isBlank() || textPositions == null || textPositions.isEmpty()) {
            return;
        }

        TextPosition first = textPositions.get(0);
        float x = first.getXDirAdj();
        float y = first.getYDirAdj();
        float fontSize = first.getFontSizeInPt();
        String fontName = first.getFont().getName();

        fontCount.put(fontName, fontCount.getOrDefault(fontName, 0) + 1);
        fontSizes.add(fontSize);
        currentPageXs.add(x);

        ColumnLayout rollingLayout = detectColumnLayout(currentPageWidth, currentPageXs);
        currentColumns = rollingLayout.columns();
        currentSplitX = rollingLayout.splitX();
        int currentColumn = detectColumnFromX(x, currentPageWidth, currentColumns, currentSplitX);

        if (paragraphs.isEmpty()) {
            paragraphs.add(new Paragraph(text, currentPage, x, y, fontName, fontSize));
            return;
        }

        Paragraph last = paragraphs.get(paragraphs.size() - 1);
        int lastColumn = detectColumnFromX(last.getX(), currentPageWidth, currentColumns, currentSplitX);
        boolean samePage = last.getPage() == currentPage;
        boolean sameColumn = lastColumn == currentColumn;
        boolean nearSameLine = Math.abs(y - last.getY()) < fontSize * 1.5f;
        boolean nearSameBlockX = Math.abs(x - last.getX()) < Math.max(80f, fontSize * 8f);

        if (samePage && sameColumn && nearSameLine && nearSameBlockX) {
            last.appendText(" " + text);
        } else {
            paragraphs.add(new Paragraph(text, currentPage, x, y, fontName, fontSize));
        }
    }

    private int columnIndexForParagraph(Paragraph p) {
        PageMeta meta = layoutInfo.get(p.getPage());
        if (meta == null) {
            return 0;
        }
        return detectColumnFromX(p.getX(), meta.getWidth(), meta.getColumnCount(), meta.getSplitX());
    }

    private ColumnLayout detectColumnLayout(float pageWidth, List<Float> xPositions) {
        if (xPositions == null || xPositions.size() < 10) {
            return new ColumnLayout(1, Float.NaN);
        }

        List<Float> sorted = new ArrayList<>(xPositions);
        sorted.sort(Float::compare);

        float minX = sorted.get(0);
        float maxX = sorted.get(sorted.size() - 1);
        float spread = maxX - minX;
        if (spread < pageWidth * 0.35f) {
            return new ColumnLayout(1, Float.NaN);
        }

        float bestGap = 0f;
        float splitX = Float.NaN;
        float midMin = minX + spread * 0.25f;
        float midMax = minX + spread * 0.75f;

        for (int i = 1; i < sorted.size(); i++) {
            float left = sorted.get(i - 1);
            float right = sorted.get(i);
            float gap = right - left;
            float candidateSplit = (left + right) / 2f;

            if (candidateSplit < midMin || candidateSplit > midMax) {
                continue;
            }
            if (gap > bestGap) {
                bestGap = gap;
                splitX = candidateSplit;
            }
        }

        if (Float.isNaN(splitX) || bestGap < pageWidth * 0.06f) {
            return new ColumnLayout(1, Float.NaN);
        }

        final float detectedSplitX = splitX;
        long leftCount = sorted.stream().filter(x -> x < detectedSplitX).count();
        long rightCount = sorted.size() - leftCount;
        int minPerSide = Math.max(3, sorted.size() / 5);

        if (leftCount < minPerSide || rightCount < minPerSide) {
            return new ColumnLayout(1, Float.NaN);
        }

        return new ColumnLayout(2, detectedSplitX);
    }

    private int detectColumnFromX(float x, float pageWidth, int columns, float splitX) {
        if (columns < 2) {
            return 0;
        }
        if (!Float.isNaN(splitX)) {
            return x < splitX ? 0 : 1;
        }
        return x < (pageWidth / 2f) ? 0 : 1;
    }

    private record ColumnLayout(int columns, float splitX) {
    }
}
