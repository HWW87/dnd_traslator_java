package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

public class PdfRebuilderService {

    private static final Logger logger = LoggerFactory.getLogger(PdfRebuilderService.class);

    private static final String CJK_FONT_ENV_VAR = "DND_CJK_FONT_PATH";
    private static final String CJK_RESOURCE_PATH = "/fonts/NotoSansCJKsc-Regular.otf";
    private static final String WINDOWS_FONTS_DIR = "C:/Windows/Fonts";
    private static final String[] CJK_FONT_KEYWORDS = new String[]{
            "notosanscjk", "sourcehansans", "simhei", "simsun", "simkai",
            "msyh", "msjh", "meiryo", "msgothic", "yu gothic", "malgun",
            "mingliu", "pmingliu", "batang", "gulim", "dotum", "deng"
    };
    private static final float IMAGE_TEXT_PADDING = 10f;
    private static final float MIN_TEXT_BASELINE = 24f;

    private final Map<Integer, Boolean> glyphSupportCache = new HashMap<>();
    private final PdfImageExtractor imageExtractor;
    private final PageLayoutBuilder pageLayoutBuilder;
    private final TextLayoutEngine textLayoutEngine;

    public PdfRebuilderService() {
        this(new PdfImageExtractor(), new PageLayoutBuilder(), new TextLayoutEngine());
    }

    PdfRebuilderService(PdfImageExtractor imageExtractor) {
        this(imageExtractor, new PageLayoutBuilder(), new TextLayoutEngine());
    }

    PdfRebuilderService(PdfImageExtractor imageExtractor, PageLayoutBuilder pageLayoutBuilder, TextLayoutEngine textLayoutEngine) {
        this.imageExtractor = imageExtractor;
        this.pageLayoutBuilder = pageLayoutBuilder;
        this.textLayoutEngine = textLayoutEngine;
    }

    public void rebuild(String originalPath, List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) throws IOException {
        if ((paragraphs == null || paragraphs.isEmpty()) && (layoutInfo == null || layoutInfo.isEmpty())) {
            throw new IllegalArgumentException("No hay parrafos para reconstruir el PDF.");
        }

        Map<Integer, List<PdfImagePlacement>> imagesByPage = extractImagesSafely(originalPath);
        Map<Integer, List<Paragraph>> paragraphsByPage = groupParagraphsByPage(paragraphs);
        TreeSet<Integer> pageNumbers = collectPageNumbers(paragraphsByPage, layoutInfo, imagesByPage);

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
                logger.warn("No se encontro fuente CJK fallback. Define {} con ruta a .ttf/.otf para evitar reemplazos por '?'.", CJK_FONT_ENV_VAR);
            }

            Map<Integer, List<String>> overflowByPage = new HashMap<>();
            while (!pageNumbers.isEmpty()) {
                Integer pageNumber = pageNumbers.pollFirst();
                PageMeta meta = layoutInfo.getOrDefault(pageNumber,
                        new PageMeta(PDRectangle.LETTER.getWidth(), PDRectangle.LETTER.getHeight(), 50, 50, 1, "NotoSans", 12));

                PDPage page = new PDPage(new PDRectangle(meta.getWidth(), meta.getHeight()));
                doc.addPage(page);

                try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                    List<PdfImagePlacement> pageImages = imagesByPage.getOrDefault(pageNumber, List.of());
                    PageLayout pageLayout = buildPageLayout(meta, pageImages);
                    drawImages(doc, cs, page.getMediaBox(), pageImages);
                    List<String> carryOver = overflowByPage.getOrDefault(pageNumber, List.of());
                    List<String> overflow = writeParagraphs(
                            cs,
                            font,
                            meta,
                            paragraphsByPage.getOrDefault(pageNumber, List.of()),
                            pageLayout,
                            carryOver
                    );
                    if (!overflow.isEmpty()) {
                        int nextPage = pageNumber + 1;
                        overflowByPage.computeIfAbsent(nextPage, ignored -> new ArrayList<>()).addAll(overflow);
                        pageNumbers.add(nextPage);
                    }
                }
            }

            File outFile = new File(originalPath.replace(".pdf", "_translated_layout.pdf"));
            doc.save(outFile);
            logger.info("PDF traducido con layout guardado en: {}", outFile.getAbsolutePath());
        }
    }

    private Map<Integer, List<PdfImagePlacement>> extractImagesSafely(String originalPath) {
        try {
            return imageExtractor.extract(originalPath);
        } catch (IOException e) {
            logger.warn("No se pudieron extraer imagenes del PDF original. Se continuara sin imagenes: {}", e.getMessage());
            return Map.of();
        }
    }

    private Map<Integer, List<Paragraph>> groupParagraphsByPage(List<Paragraph> paragraphs) {
        Map<Integer, List<Paragraph>> paragraphsByPage = new HashMap<>();
        if (paragraphs == null) {
            return paragraphsByPage;
        }

        for (Paragraph paragraph : paragraphs) {
            paragraphsByPage.computeIfAbsent(paragraph.getPage(), ignored -> new ArrayList<>()).add(paragraph);
        }

        for (List<Paragraph> pageParagraphs : paragraphsByPage.values()) {
            pageParagraphs.sort(Comparator.comparing(Paragraph::getY).thenComparing(Paragraph::getX));
        }

        return paragraphsByPage;
    }

    private TreeSet<Integer> collectPageNumbers(
            Map<Integer, List<Paragraph>> paragraphsByPage,
            Map<Integer, PageMeta> layoutInfo,
            Map<Integer, List<PdfImagePlacement>> imagesByPage
    ) {
        TreeSet<Integer> pageNumbers = new TreeSet<>();
        pageNumbers.addAll(paragraphsByPage.keySet());
        if (layoutInfo != null) {
            pageNumbers.addAll(layoutInfo.keySet());
        }
        pageNumbers.addAll(imagesByPage.keySet());
        return pageNumbers;
    }

    private PageLayout buildPageLayout(PageMeta meta, List<PdfImagePlacement> pageImages) {
        List<BlockedRegion> blockedRegions = new ArrayList<>();
        for (PdfImagePlacement imagePlacement : pageImages) {
            if (imagePlacement == null || !imagePlacement.isRenderable()) {
                continue;
            }
            blockedRegions.add(new BlockedRegion(
                    imagePlacement.x(),
                    imagePlacement.y(),
                    imagePlacement.width(),
                    imagePlacement.height()
            ));
        }

        float margin = Math.max(meta.getLeftMargin(), 24f);
        return pageLayoutBuilder.build(meta.getWidth(), meta.getHeight(), margin, blockedRegions);
    }

    private void drawImages(
            PDDocument document,
            PDPageContentStream contentStream,
            PDRectangle pageBox,
            List<PdfImagePlacement> placements
    ) {
        for (PdfImagePlacement placement : placements) {
            if (placement == null || !placement.isRenderable()) {
                continue;
            }

            float x = clamp(placement.x(), 0f, pageBox.getWidth());
            float y = clamp(placement.y(), 0f, pageBox.getHeight());
            float width = Math.min(placement.width(), pageBox.getWidth() - x);
            float height = Math.min(placement.height(), pageBox.getHeight() - y);

            if (width <= 1f || height <= 1f) {
                logger.warn("Se omite imagen con bounding box invalido en pagina {} ({})", placement.pageNumber(), placement.placementStrategy());
                continue;
            }

            try {
                PDImageXObject imageObject = LosslessFactory.createFromImage(document, placement.image());
                contentStream.drawImage(imageObject, x, y, width, height);
            } catch (IOException e) {
                logger.warn(
                        "No se pudo reinsertar imagen en pagina {} ({}): {}",
                        placement.pageNumber(),
                        placement.placementStrategy(),
                        e.getMessage()
                );
            }
        }
    }

    private List<String> writeParagraphs(
            PDPageContentStream cs,
            PDType0Font font,
            PageMeta meta,
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
                        cs,
                        pageLayout.textBoxes(),
                        sanitizeForFont(carryOver, font),
                        font,
                        Math.max(meta.getLeftMargin(), 24f),
                        meta.getHeight() - Math.max(meta.getTopMargin(), 24f)
                );
                if (!rendered.remainingText().isBlank()) {
                    overflowTexts.add(rendered.remainingText());
                }
            }
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
                    cs,
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

    float resolveSafeY(
            PDType0Font font,
            String text,
            float x,
            float requestedY,
            float fontSize,
            float maxWidth,
            PageMeta meta,
            List<PdfImagePlacement> pageImages
    ) throws IOException {
        if (pageImages == null || pageImages.isEmpty() || text == null || text.isBlank()) {
            return requestedY;
        }

        float safeY = requestedY;
        int lineCount = estimateLineCount(font, text, fontSize, maxWidth);
        float leading = 1.2f * fontSize;
        float textTop = safeY + fontSize;
        float textBottom = safeY - ((Math.max(1, lineCount) - 1) * leading);
        float textLeft = x;
        float textRight = x + maxWidth;

        boolean moved;
        int guard = 0;
        do {
            moved = false;
            for (PdfImagePlacement image : pageImages) {
                if (image == null || !image.isRenderable()) {
                    continue;
                }

                float imageLeft = image.x();
                float imageRight = image.x() + image.width();
                float imageBottom = image.y();
                float imageTop = image.y() + image.height();

                boolean overlapsHorizontally = textRight > imageLeft && textLeft < imageRight;
                boolean overlapsVertically = textTop > imageBottom && textBottom < imageTop;
                if (!overlapsHorizontally || !overlapsVertically) {
                    continue;
                }

                safeY = Math.max(MIN_TEXT_BASELINE, imageBottom - IMAGE_TEXT_PADDING);
                textTop = safeY + fontSize;
                textBottom = safeY - ((Math.max(1, lineCount) - 1) * leading);
                moved = true;
            }
            guard++;
        } while (moved && guard < 8);

        float topLimit = meta.getHeight() - Math.max(meta.getTopMargin(), fontSize + 4f);
        return clamp(safeY, MIN_TEXT_BASELINE, topLimit);
    }

    private int estimateLineCount(PDType0Font font, String text, float fontSize, float maxWidth) throws IOException {
        String[] words = text.replace("\t", " ").split("\\s+");
        if (words.length == 0) {
            return 1;
        }

        int lines = 1;
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            String testLine = currentLine + word + " ";
            float textWidth = font.getStringWidth(testLine) / 1000 * fontSize;
            if (textWidth > maxWidth && !currentLine.isEmpty()) {
                lines++;
                currentLine = new StringBuilder(word + " ");
            } else if (textWidth > maxWidth) {
                lines++;
                currentLine = new StringBuilder();
            } else {
                currentLine.append(word).append(" ");
            }
        }
        return Math.max(1, lines);
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
                logger.info("Usando fuente CJK embebida: {}", CJK_RESOURCE_PATH);
                return resourceFont;
            }
        } catch (IOException e) {
            logger.warn("No se pudo cargar fuente CJK embebida: {}", e.getMessage());
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
            logger.info("Usando fuente CJK ({}): {}", sourceLabel, file.getAbsolutePath());
            return loaded;
        } catch (IOException e) {
            return null;
        }
    }

    private boolean isSupportedFontExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
