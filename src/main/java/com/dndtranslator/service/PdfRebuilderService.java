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
import java.util.Map;
import java.util.TreeSet;

public class PdfRebuilderService {

    private static final Logger logger = LoggerFactory.getLogger(PdfRebuilderService.class);

    private final PdfImageExtractor imageExtractor;
    private final PageLayoutBuilder pageLayoutBuilder;
    private final PageAnalyzer pageAnalyzer;
    private final PageTypeClassifier pageTypeClassifier;
    private final PageLayoutStrategyFactory pageLayoutStrategyFactory;
    private final FontResolver fontResolver;
    private final PageTextRenderer pageTextRenderer;

    public PdfRebuilderService() {
        this(new PdfImageExtractor(), new PageLayoutBuilder(), new TextLayoutEngine(), new FontResolver());
    }

    PdfRebuilderService(PdfImageExtractor imageExtractor) {
        this(imageExtractor, new PageLayoutBuilder(), new TextLayoutEngine(), new FontResolver());
    }

    PdfRebuilderService(PdfImageExtractor imageExtractor, PageLayoutBuilder pageLayoutBuilder, TextLayoutEngine textLayoutEngine) {
        this(imageExtractor, pageLayoutBuilder, textLayoutEngine, new FontResolver());
    }

    PdfRebuilderService(
            PdfImageExtractor imageExtractor,
            PageLayoutBuilder pageLayoutBuilder,
            TextLayoutEngine textLayoutEngine,
            FontResolver fontResolver
    ) {
        this.imageExtractor = imageExtractor;
        this.pageLayoutBuilder = pageLayoutBuilder;
        this.pageAnalyzer = new PageAnalyzer();
        this.pageTypeClassifier = new PageTypeClassifier();
        this.pageLayoutStrategyFactory = new PageLayoutStrategyFactory(pageLayoutBuilder);
        this.fontResolver = fontResolver;
        this.pageTextRenderer = new PageTextRenderer(textLayoutEngine);
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

            PDType0Font cjkFont = fontResolver.resolveCjkFont(doc, this.getClass());

            if (cjkFont != null) {
                font = cjkFont;
            } else {
                logger.warn("No se encontro fuente CJK fallback. Define {} con ruta a .ttf/.otf para evitar reemplazos por '?'.", FontResolver.CJK_FONT_ENV_VAR);
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
                    List<Paragraph> pageParagraphs = paragraphsByPage.getOrDefault(pageNumber, List.of());

                    PageAnalysisData analysisData = pageAnalyzer.analyze(pageNumber, meta, pageImages, pageParagraphs);
                    PageType pageType = pageTypeClassifier.classify(analysisData);
                    PageLayoutStrategy strategy = pageLayoutStrategyFactory.getStrategy(pageType);
                    PageRenderContext renderContext = new PageRenderContext(
                            pageNumber,
                            meta,
                            pageImages,
                            pageParagraphs,
                            pageType,
                            analysisData
                    );
                    strategy.renderPage(renderContext);

                    PageLayout pageLayout = ensureLayoutFallback(renderContext, meta, pageImages);
                    logger.info(
                            "Pagina {} detectada como {} -> estrategia {}",
                            pageNumber,
                            pageType,
                            strategy.getClass().getSimpleName()
                    );

                    drawImages(doc, cs, page.getMediaBox(), pageImages);
                    List<String> carryOver = overflowByPage.getOrDefault(pageNumber, List.of());
                    List<String> overflow = pageTextRenderer.writeParagraphs(
                            cs,
                            font,
                            meta,
                            pageParagraphs,
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

    private PageLayout ensureLayoutFallback(PageRenderContext context, PageMeta meta, List<PdfImagePlacement> pageImages) {
        PageLayout strategyLayout = context.getPageLayout();
        if (strategyLayout == null || strategyLayout.textBoxes() == null || strategyLayout.textBoxes().isEmpty()) {
            return buildPageLayout(meta, pageImages);
        }
        return strategyLayout;
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


    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(value, max));
    }
}
