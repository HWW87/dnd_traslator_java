package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import net.sourceforge.tess4j.ITessAPI;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import net.sourceforge.tess4j.Word;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PdfToParagraphService {

    private static final int OCR_DPI = 300;
    private static final float OCR_TO_PDF_UNITS = 72f / OCR_DPI;

    private final Map<Integer, PageMeta> layoutInfo = new HashMap<>();

    public Map<Integer, PageMeta> getLayoutInfo() {
        return layoutInfo;
    }

    public List<Paragraph> extractParagraphsFromPdf(File pdf) throws Exception {
        List<Paragraph> result = new ArrayList<>();
        layoutInfo.clear();

        List<PageImageRef> renderedPages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);

            // Render secuencial: PDFRenderer comparte estado interno del documento.
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, OCR_DPI);
                float width = image.getWidth() * OCR_TO_PDF_UNITS;
                float height = image.getHeight() * OCR_TO_PDF_UNITS;
                renderedPages.add(new PageImageRef(pageIndex, image, width, height));
            }
        }

        int ocrThreads = resolveOcrThreads();
        ExecutorService ocrPool = Executors.newFixedThreadPool(ocrThreads);
        List<Future<PageOcrResult>> futures = new ArrayList<>();

        try {
            for (PageImageRef page : renderedPages) {
                futures.add(ocrPool.submit(() -> extractParagraphsFromImage(page)));
            }

            for (Future<PageOcrResult> future : futures) {
                PageOcrResult pageResult = future.get();
                layoutInfo.put(pageResult.pageNumber(), pageResult.meta());
                result.addAll(pageResult.paragraphs());
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Error OCR embebido: " + cause.getMessage(), cause);
        } finally {
            ocrPool.shutdownNow();
        }

        result.sort(Comparator
                .comparingInt(Paragraph::getPage)
                .thenComparingInt(this::columnIndexForParagraph)
                .thenComparing(Paragraph::getY)
                .thenComparing(Paragraph::getX));

        return result;
    }

    private PageOcrResult extractParagraphsFromImage(PageImageRef page) throws IOException, TesseractException {
        ITesseract tesseract = createTesseract();
        int pageNumber = page.pageIndex() + 1;

        List<Paragraph> primary = extractLineParagraphs(tesseract, page.image(), pageNumber);
        BufferedImage preprocessed = preprocessForOcr(page.image());
        List<Paragraph> enhanced = extractLineParagraphs(tesseract, preprocessed, pageNumber);

        List<Paragraph> selected = scoreParagraphs(enhanced) >= scoreParagraphs(primary) ? enhanced : primary;
        ColumnLayout layout = detectColumnLayout(page.pageWidth(), xPositions(selected));

        selected.sort(Comparator
                .comparing(Paragraph::getPage)
                .thenComparingInt(p -> columnIndex(p, layout.columns(), layout.splitX(), page.pageWidth()))
                .thenComparing(Paragraph::getY)
                .thenComparing(Paragraph::getX));

        PageMeta meta = new PageMeta(
                page.pageWidth(),
                page.pageHeight(),
                50f,
                50f,
                layout.columns(),
                "OCR",
                averageFontSize(selected),
                layout.splitX()
        );
        return new PageOcrResult(pageNumber, selected, meta);
    }

    private List<Paragraph> extractLineParagraphs(ITesseract tesseract, BufferedImage image, int pageNumber) throws TesseractException {
        List<Word> lines = tesseract.getWords(image, ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE);
        List<Paragraph> blocks = new ArrayList<>();

        for (Word line : lines) {
            String text = normalize(line.getText());
            if (text.isBlank()) {
                continue;
            }

            Rectangle bbox = line.getBoundingBox();
            float x = bbox != null ? bbox.x * OCR_TO_PDF_UNITS : 0f;
            float y = bbox != null ? bbox.y * OCR_TO_PDF_UNITS : 0f;
            float fontSize = bbox != null ? Math.max(8f, (bbox.height * OCR_TO_PDF_UNITS) * 0.7f) : 10f;

            blocks.add(new Paragraph(text, pageNumber, x, y, "OCR", fontSize));
        }

        return blocks;
    }

    private BufferedImage preprocessForOcr(BufferedImage source) {
        BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        int threshold = 175;

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                Color c = new Color(rgb);
                int gray = (int) (0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue());
                int bw = gray > threshold ? 255 : 0;
                int packed = (bw << 16) | (bw << 8) | bw;
                out.setRGB(x, y, packed);
            }
        }
        return out;
    }

    private double scoreParagraphs(List<Paragraph> paragraphs) {
        if (paragraphs.isEmpty()) {
            return 0d;
        }

        double totalChars = paragraphs.stream().mapToInt(p -> p.getFullText().length()).sum();
        long veryShort = paragraphs.stream().filter(p -> p.getFullText().length() < 3).count();
        double avgLen = totalChars / paragraphs.size();
        double shortPenalty = (double) veryShort / paragraphs.size();
        return avgLen - (shortPenalty * 10d);
    }

    private ITesseract createTesseract() throws IOException {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(resolveTessdataPath().toString());
        tesseract.setLanguage(resolveOcrLang());
        tesseract.setPageSegMode(ITessAPI.TessPageSegMode.PSM_AUTO);
        tesseract.setOcrEngineMode(ITessAPI.TessOcrEngineMode.OEM_LSTM_ONLY);
        return tesseract;
    }

    private Path resolveTessdataPath() throws IOException {
        List<Path> candidates = new ArrayList<>();

        String custom = System.getenv("DND_TESSDATA_PATH");
        if (custom != null && !custom.isBlank()) {
            Path customPath = Paths.get(custom.trim());
            addPathAndPossibleTessdataChild(candidates, customPath);
        }

        String tessdataPrefix = System.getenv("TESSDATA_PREFIX");
        if (tessdataPrefix != null && !tessdataPrefix.isBlank()) {
            addPathAndPossibleTessdataChild(candidates, Paths.get(tessdataPrefix.trim()));
        }

        addPathAndPossibleTessdataChild(candidates, Paths.get("tessdata"));
        addPathAndPossibleTessdataChild(candidates, Paths.get("src", "main", "resources", "tessdata"));

        String programFiles = System.getenv("ProgramFiles");
        if (programFiles != null && !programFiles.isBlank()) {
            addPathAndPossibleTessdataChild(candidates, Paths.get(programFiles, "Tesseract-OCR", "tessdata"));
        }

        String programFilesX86 = System.getenv("ProgramFiles(x86)");
        if (programFilesX86 != null && !programFilesX86.isBlank()) {
            addPathAndPossibleTessdataChild(candidates, Paths.get(programFilesX86, "Tesseract-OCR", "tessdata"));
        }

        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null && !localAppData.isBlank()) {
            addPathAndPossibleTessdataChild(candidates, Paths.get(localAppData, "Programs", "Tesseract-OCR", "tessdata"));
            addPathAndPossibleTessdataChild(candidates, Paths.get(localAppData, "Tesseract-OCR", "tessdata"));
        }

        List<String> tested = new ArrayList<>();
        for (Path candidate : candidates) {
            Path normalized = candidate.toAbsolutePath().normalize();
            tested.add(normalized.toString());
            if (isValidTessdataDir(normalized)) {
                return normalized;
            }
        }

        throw new IOException(
                "No se encontro carpeta tessdata. Configura DND_TESSDATA_PATH. Rutas probadas: " + String.join(" | ", tested)
        );
    }

    private void addPathAndPossibleTessdataChild(List<Path> candidates, Path path) {
        if (path == null) {
            return;
        }
        candidates.add(path);

        Path fileName = path.getFileName();
        boolean isTessdataDir = fileName != null && fileName.toString().equalsIgnoreCase("tessdata");
        if (!isTessdataDir) {
            candidates.add(path.resolve("tessdata"));
        }
    }

    private boolean isValidTessdataDir(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return false;
        }
        Path spa = path.resolve("spa.traineddata");
        Path eng = path.resolve("eng.traineddata");
        return Files.exists(spa) || Files.exists(eng);
    }

    private String resolveOcrLang() {
        String lang = System.getenv("DND_OCR_LANG");
        if (lang == null || lang.isBlank()) {
            return "spa+eng";
        }
        return lang.trim().toLowerCase(Locale.ROOT);
    }

    private int columnIndexForParagraph(Paragraph p) {
        PageMeta meta = layoutInfo.get(p.getPage());
        if (meta == null) {
            return 0;
        }
        return columnIndex(p, meta.getColumnCount(), meta.getSplitX(), meta.getWidth());
    }

    private int columnIndex(Paragraph p, int columns, float splitX, float pageWidth) {
        if (columns < 2) {
            return 0;
        }
        if (!Float.isNaN(splitX)) {
            return p.getX() < splitX ? 0 : 1;
        }
        return p.getX() < (pageWidth / 2f) ? 0 : 1;
    }

    private float averageFontSize(List<Paragraph> paragraphs) {
        return (float) paragraphs.stream().mapToDouble(Paragraph::getFontSize).average().orElse(11d);
    }

    private List<Float> xPositions(List<Paragraph> paragraphs) {
        return paragraphs.stream().map(Paragraph::getX).sorted().toList();
    }

    private ColumnLayout detectColumnLayout(float pageWidth, List<Float> xPositions) {
        if (xPositions == null || xPositions.size() < 10) {
            return new ColumnLayout(1, Float.NaN);
        }

        float minX = xPositions.get(0);
        float maxX = xPositions.get(xPositions.size() - 1);
        float spread = maxX - minX;
        if (spread < pageWidth * 0.32f) {
            return new ColumnLayout(1, Float.NaN);
        }

        float bestGap = 0f;
        float splitX = Float.NaN;
        float midMin = minX + spread * 0.15f;
        float midMax = minX + spread * 0.85f;

        for (int i = 1; i < xPositions.size(); i++) {
            float left = xPositions.get(i - 1);
            float right = xPositions.get(i);
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

        if (!Float.isNaN(splitX)) {
            final float detectedSplitX = splitX;
            long leftCount = xPositions.stream().filter(x -> x < detectedSplitX).count();
            long rightCount = xPositions.size() - leftCount;
            int minPerSide = Math.max(3, xPositions.size() / 8);

            if (bestGap >= pageWidth * 0.03f && leftCount >= minPerSide && rightCount >= minPerSide) {
                return new ColumnLayout(2, detectedSplitX);
            }
        }

        float splitByMedian = xPositions.get(xPositions.size() / 2);
        long left = xPositions.stream().filter(x -> x < splitByMedian).count();
        long right = xPositions.size() - left;

        int minPerSide = Math.max(3, xPositions.size() / 6);
        if (left < minPerSide || right < minPerSide) {
            return new ColumnLayout(1, Float.NaN);
        }

        double leftAvg = xPositions.stream().filter(x -> x < splitByMedian).mapToDouble(Float::doubleValue).average().orElse(minX);
        double rightAvg = xPositions.stream().filter(x -> x >= splitByMedian).mapToDouble(Float::doubleValue).average().orElse(maxX);
        double centroidSeparation = rightAvg - leftAvg;

        if (centroidSeparation < pageWidth * 0.20f) {
            return new ColumnLayout(1, Float.NaN);
        }

        return new ColumnLayout(2, splitByMedian);
    }

    private int resolveOcrThreads() {
        String raw = System.getenv("DND_OCR_THREADS");
        int fallback = Math.max(1, Runtime.getRuntime().availableProcessors());
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            int configured = Integer.parseInt(raw.trim());
            return configured > 0 ? configured : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private String normalize(String s) {
        return Optional.ofNullable(s).orElse("").replaceAll("\\s+", " ").trim();
    }

    private record PageImageRef(int pageIndex, BufferedImage image, float pageWidth, float pageHeight) {
    }

    private record PageOcrResult(int pageNumber, List<Paragraph> paragraphs, PageMeta meta) {
    }

    private record ColumnLayout(int columns, float splitX) {
    }
}
