package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PdfRebuilderService {

    private static final String CJK_FONT_ENV_VAR = "DND_CJK_FONT_PATH";
    private static final String CJK_RESOURCE_PATH = "/fonts/NotoSansCJKsc-Regular.otf";
    private static final String WINDOWS_FONTS_DIR = "C:/Windows/Fonts";
    private static final String[] CJK_FONT_KEYWORDS = new String[]{
            "notosanscjk", "sourcehansans", "simhei", "simsun", "simkai",
            "msyh", "msjh", "meiryo", "msgothic", "yu gothic", "malgun",
            "mingliu", "pmingliu", "batang", "gulim", "dotum", "deng"
    };

    private final Map<Integer, Boolean> glyphSupportCache = new HashMap<>();

    public void rebuild(String originalPath, List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) throws IOException {
        if (paragraphs == null || paragraphs.isEmpty()) {
            throw new IllegalArgumentException("No hay parrafos para reconstruir el PDF.");
        }

        try (PDDocument doc = new PDDocument()) {
            PDType0Font font;
            try (InputStream fontStream = this.getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
                if (fontStream == null) {
                    throw new FileNotFoundException("No se encontro /fonts/NotoSans-Regular.ttf en recursos.");
                }
                font = PDType0Font.load(doc, fontStream);
            }

            PDType0Font cjkFont = resolveCjkFont(doc);

            if (cjkFont != null) {
                font = cjkFont;
            } else {
                System.err.println("No se encontro fuente CJK fallback. Define " + CJK_FONT_ENV_VAR + " con ruta a .ttf/.otf para evitar reemplazos por '?'.");
            }

            int currentPageNumber = -1;
            PDPage page = null;
            PDPageContentStream cs = null;

            for (Paragraph paragraph : paragraphs) {
                int pageNumber = paragraph.getPage();

                if (pageNumber != currentPageNumber) {
                    if (cs != null) {
                        cs.close();
                    }

                    PageMeta meta = layoutInfo.getOrDefault(pageNumber,
                            new PageMeta(PDRectangle.LETTER.getWidth(), PDRectangle.LETTER.getHeight(), 50, 50, 1, "NotoSans", 12));

                    page = new PDPage(new PDRectangle(meta.getWidth(), meta.getHeight()));
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    currentPageNumber = pageNumber;
                }

                String text = paragraph.getTranslatedText();
                if (text == null || text.isBlank()) {
                    continue;
                }

                PageMeta meta = layoutInfo.getOrDefault(pageNumber,
                        new PageMeta(PDRectangle.LETTER.getWidth(), PDRectangle.LETTER.getHeight(), 50, 50, 1, "NotoSans", 12));

                float x = paragraph.getX();
                float y = paragraph.getY();
                float fontSize = paragraph.getFontSize();
                if (y < 50) {
                    y = 60;
                }

                float maxWidth = computeMaxWidthForParagraph(x, meta);
                if (maxWidth < 40f) {
                    maxWidth = 120f;
                }

                String safeText = sanitizeForFont(text, font);
                if (safeText.isBlank()) {
                    continue;
                }

                writeTextAt(cs, font, safeText, x, y, fontSize, maxWidth);
            }

            if (cs != null) {
                cs.close();
            }

            File outFile = new File(originalPath.replace(".pdf", "_translated_layout.pdf"));
            doc.save(outFile);
            System.out.println("PDF traducido con layout guardado en: " + outFile.getAbsolutePath());
        }
    }

    private void writeTextAt(PDPageContentStream cs, PDType0Font font, String text, float x, float y, float fontSize, float maxWidth) throws IOException {
        float leading = 1.2f * fontSize;

        String[] words = text.replace("\t", " ").split("\\s+");
        StringBuilder line = new StringBuilder();

        cs.beginText();
        cs.setFont(font, fontSize);
        cs.newLineAtOffset(x, y);

        for (String word : words) {
            String testLine = line + word + " ";
            float textWidth = font.getStringWidth(testLine) / 1000 * fontSize;

            if (textWidth > maxWidth) {
                if (!line.isEmpty()) {
                    cs.showText(line.toString().trim());
                    cs.newLineAtOffset(0, -leading);
                    line = new StringBuilder(word + " ");
                } else {
                    cs.showText(word);
                    cs.newLineAtOffset(0, -leading);
                }
            } else {
                line.append(word).append(" ");
            }
        }

        if (!line.isEmpty()) {
            cs.showText(line.toString().trim());
        }

        cs.endText();
    }

    private float computeMaxWidthForParagraph(float x, PageMeta meta) {
        float margin = Math.max(30f, meta.getLeftMargin());
        float pageWidth = meta.getWidth();
        int columns = Math.max(1, meta.getColumnCount());

        if (columns <= 1) {
            return (pageWidth - margin) - x;
        }

        float gutter = 24f;
        float splitX = Float.isNaN(meta.getSplitX()) ? (pageWidth / 2f) : meta.getSplitX();

        float leftStart = margin;
        float leftEnd = splitX - (gutter / 2f);
        float rightStart = splitX + (gutter / 2f);
        float rightEnd = pageWidth - margin;

        // Fallback defensivo si el split/gutter deja columnas invalidas.
        if (leftEnd <= leftStart + 20f || rightEnd <= rightStart + 20f) {
            float totalUsableWidth = pageWidth - (margin * 2f) - gutter;
            float columnWidth = totalUsableWidth / 2f;
            leftEnd = margin + columnWidth;
            rightStart = leftEnd + gutter;
        }

        boolean leftColumn = x < splitX;
        float columnStart = leftColumn ? leftStart : rightStart;
        float columnEnd = leftColumn ? leftEnd : rightEnd;

        float clampedX = Math.max(columnStart, Math.min(x, columnEnd - 10f));
        return Math.max(60f, columnEnd - clampedX);
    }

    private String sanitizeForFont(String text, PDType0Font font) {
        StringBuilder safe = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            String ch = new String(Character.toChars(codePoint));
            if (isGlyphSupported(font, codePoint)) {
                safe.append(ch);
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

    private PDType0Font resolveCjkFont(PDDocument doc) {
        try (InputStream cjkStream = this.getClass().getResourceAsStream(CJK_RESOURCE_PATH)) {
            if (cjkStream != null) {
                PDType0Font resourceFont = PDType0Font.load(doc, cjkStream);
                System.out.println("Usando fuente CJK embebida: " + CJK_RESOURCE_PATH);
                return resourceFont;
            }
        } catch (IOException e) {
            System.err.println("No se pudo cargar fuente CJK embebida: " + e.getMessage());
        }

        String customPath = System.getenv(CJK_FONT_ENV_VAR);
        PDType0Font custom = tryLoadFontFile(doc, customPath, "entorno");
        if (custom != null) {
            return custom;
        }

        for (String candidate : findWindowsCjkCandidates()) {
            PDType0Font systemFont = tryLoadFontFile(doc, candidate, "sistema");
            if (systemFont != null) {
                return systemFont;
            }
        }

        return null;
    }

    private List<String> findWindowsCjkCandidates() {
        List<String> candidates = new ArrayList<>();

        String[] fixedCandidates = new String[]{
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simsun.ttf",
                "C:/Windows/Fonts/simkai.ttf",
                "C:/Windows/Fonts/msyh.ttf",
                "C:/Windows/Fonts/meiryo.ttf",
                "C:/Windows/Fonts/malgun.ttf",
                "C:/Windows/Fonts/arialuni.ttf",
                "C:/Windows/Fonts/NotoSansCJKsc-Regular.otf"
        };
        for (String candidate : fixedCandidates) {
            candidates.add(candidate);
        }

        File fontsDir = new File(WINDOWS_FONTS_DIR);
        if (!fontsDir.isDirectory()) {
            return candidates;
        }

        File[] dynamicCandidates = fontsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!isSupportedFontExtension(lower)) {
                return false;
            }
            for (String keyword : CJK_FONT_KEYWORDS) {
                if (lower.contains(keyword)) {
                    return true;
                }
            }
            return false;
        });

        if (dynamicCandidates != null) {
            for (File file : dynamicCandidates) {
                candidates.add(file.getAbsolutePath());
            }
        }

        return candidates;
    }

    private PDType0Font tryLoadFontFile(PDDocument doc, String filePath, String sourceLabel) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        File file = new File(filePath.trim());
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        if (!isSupportedFontExtension(file.getName())) {
            return null;
        }

        try {
            PDType0Font loaded = PDType0Font.load(doc, file);
            System.out.println("Usando fuente CJK " + sourceLabel + ": " + file.getAbsolutePath());
            return loaded;
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isSupportedFontExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }
}
