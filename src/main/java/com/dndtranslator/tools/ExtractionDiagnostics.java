package com.dndtranslator.tools;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import com.dndtranslator.service.PdfExtractorService;
import com.dndtranslator.service.PdfToParagraphService;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ExtractionDiagnostics {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: ExtractionDiagnostics <pdfPath>");
            System.exit(1);
        }

        String pdfPath = String.join(" ", args).trim();
        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists() || !pdfFile.canRead()) {
            System.out.println("ERROR: PDF not found or not readable: " + pdfFile.getAbsolutePath());
            System.exit(2);
        }

        PdfExtractorService extractor = new PdfExtractorService();
        List<Paragraph> paragraphs = extractor.extractParagraphs(pdfFile.getAbsolutePath());
        Map<Integer, PageMeta> layoutInfo = extractor.getLayoutInfo();

        boolean usedOcrFallback = false;
        if (paragraphs.isEmpty()) {
            usedOcrFallback = true;
            PdfToParagraphService ocr = new PdfToParagraphService();
            paragraphs = ocr.extractParagraphsFromPdf(pdfFile);
        }

        paragraphs.sort(Comparator
                .comparingInt(Paragraph::getPage)
                .thenComparingInt(p -> columnIndexForParagraph(p, layoutInfo))
                .thenComparing(Paragraph::getY)
                .thenComparing(Paragraph::getX));

        int totalChars = paragraphs.stream().mapToInt(p -> p.getFullText().length()).sum();
        int totalPages = Math.max(
                paragraphs.stream().mapToInt(Paragraph::getPage).max().orElse(0),
                layoutInfo.keySet().stream().mapToInt(Integer::intValue).max().orElse(0)
        );

        int crossColumnJumps = computeCrossColumnJumps(paragraphs, layoutInfo);
        double noisyRatio = computeNoisyRatio(paragraphs);
        double suspiciousRatio = computeSuspiciousRatio(paragraphs);

        System.out.println("pdf=" + pdfFile.getAbsolutePath());
        System.out.println("mode=" + (usedOcrFallback ? "ocr-fallback" : "embedded-text"));
        System.out.println("pages=" + totalPages);
        System.out.println("paragraphs=" + paragraphs.size());
        System.out.println("chars=" + totalChars);
        System.out.println("crossColumnJumps=" + crossColumnJumps);
        System.out.println("noisyRatio=" + String.format("%.3f", noisyRatio));
        System.out.println("suspiciousRatio=" + String.format("%.3f", suspiciousRatio));

        if (!layoutInfo.isEmpty()) {
            String perPageColumns = layoutInfo.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> {
                        PageMeta meta = e.getValue();
                        String split = Float.isNaN(meta.getSplitX()) ? "-" : String.format("%.1f", meta.getSplitX());
                        return "p" + e.getKey() + "=" + meta.getColumnCount() + "(split=" + split + ")";
                    })
                    .collect(Collectors.joining(", "));
            System.out.println("columns=" + perPageColumns);
        }

        String pageSpread = buildPageSpread(paragraphs, layoutInfo);
        if (!pageSpread.isBlank()) {
            System.out.println("pageSpread=" + pageSpread);
        }

        String preview = paragraphs.stream()
                .limit(5)
                .map(Paragraph::getFullText)
                .collect(Collectors.joining(" | "));
        System.out.println("preview=" + preview);
    }

    private static int columnIndexForParagraph(Paragraph p, Map<Integer, PageMeta> layoutInfo) {
        PageMeta meta = layoutInfo.get(p.getPage());
        if (meta == null || meta.getColumnCount() < 2) {
            return 0;
        }
        float splitX = Float.isNaN(meta.getSplitX()) ? (meta.getWidth() / 2f) : meta.getSplitX();
        return p.getX() < splitX ? 0 : 1;
    }

    private static int computeCrossColumnJumps(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) {
        Map<Integer, List<Paragraph>> byPage = new HashMap<>();
        for (Paragraph p : paragraphs) {
            byPage.computeIfAbsent(p.getPage(), ignored -> new ArrayList<>()).add(p);
        }

        int jumps = 0;
        for (Map.Entry<Integer, List<Paragraph>> entry : byPage.entrySet()) {
            int lastCol = -1;
            for (Paragraph p : entry.getValue()) {
                int col = columnIndexForParagraph(p, layoutInfo);
                if (lastCol != -1 && col != lastCol) {
                    jumps++;
                }
                lastCol = col;
            }
        }
        return jumps;
    }

    private static double computeNoisyRatio(List<Paragraph> paragraphs) {
        long totalChars = 0;
        long noisyChars = 0;

        for (Paragraph p : paragraphs) {
            String text = p.getFullText();
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
            return 0d;
        }
        return (double) noisyChars / (double) totalChars;
    }

    private static double computeSuspiciousRatio(List<Paragraph> paragraphs) {
        long totalChars = 0;
        long suspiciousChars = 0;

        for (Paragraph p : paragraphs) {
            String text = p.getFullText();
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
            return 0d;
        }
        return (double) suspiciousChars / (double) totalChars;
    }

    private static String buildPageSpread(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) {
        Map<Integer, List<Paragraph>> byPage = new HashMap<>();
        for (Paragraph p : paragraphs) {
            byPage.computeIfAbsent(p.getPage(), ignored -> new ArrayList<>()).add(p);
        }

        return byPage.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    int page = entry.getKey();
                    List<Paragraph> pageParagraphs = entry.getValue();
                    float minX = pageParagraphs.stream().map(Paragraph::getX).min(Float::compare).orElse(0f);
                    float maxX = pageParagraphs.stream().map(Paragraph::getX).max(Float::compare).orElse(0f);

                    PageMeta meta = layoutInfo.get(page);
                    float splitX;
                    if (meta != null && !Float.isNaN(meta.getSplitX())) {
                        splitX = meta.getSplitX();
                    } else {
                        splitX = (minX + maxX) / 2f;
                    }

                    long left = pageParagraphs.stream().filter(p -> p.getX() < splitX).count();
                    long right = pageParagraphs.size() - left;
                    long total = Math.max(1, pageParagraphs.size());
                    int leftPct = (int) Math.round((left * 100.0) / total);
                    int rightPct = (int) Math.round((right * 100.0) / total);

                    return "p" + page
                            + "[xMin=" + String.format("%.1f", minX)
                            + ",xMax=" + String.format("%.1f", maxX)
                            + ",left=" + leftPct + "%"
                            + ",right=" + rightPct + "%]";
                })
                .collect(Collectors.joining(", "));
    }
}
