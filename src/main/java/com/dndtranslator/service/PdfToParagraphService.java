package com.dndtranslator.service;

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

    public List<Paragraph> extractParagraphsFromPdf(File pdf) throws Exception {
        List<Paragraph> result = new ArrayList<>();

        List<PageImageRef> renderedPages = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdf)) {
            PDFRenderer renderer = new PDFRenderer(document);

            // Render secuencial: PDFRenderer comparte estado interno del documento.
            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, OCR_DPI);
                renderedPages.add(new PageImageRef(pageIndex, image));
            }
        }

        int ocrThreads = resolveOcrThreads();
        ExecutorService ocrPool = Executors.newFixedThreadPool(ocrThreads);
        List<Future<List<Paragraph>>> futures = new ArrayList<>();

        try {
            for (PageImageRef page : renderedPages) {
                futures.add(ocrPool.submit(() -> extractParagraphsFromImage(page.pageIndex(), page.image())));
            }

            for (Future<List<Paragraph>> future : futures) {
                result.addAll(future.get());
            }
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new RuntimeException("Error OCR embebido: " + cause.getMessage(), cause);
        } finally {
            ocrPool.shutdownNow();
        }

        Map<Integer, Float> splitByPage = estimateSplitByPage(result);
        result.sort(Comparator
                .comparingInt(Paragraph::getPage)
                .thenComparingInt(p -> columnIndex(p, splitByPage.getOrDefault(p.getPage(), Float.NaN)))
                .thenComparing(Paragraph::getY)
                .thenComparing(Paragraph::getX));

        return result;
    }

    private List<Paragraph> extractParagraphsFromImage(int pageIndexZeroBased, BufferedImage image) throws IOException, TesseractException {
        ITesseract tesseract = createTesseract();

        List<Paragraph> primary = extractLineParagraphs(tesseract, image, pageIndexZeroBased + 1);
        BufferedImage preprocessed = preprocessForOcr(image);
        List<Paragraph> enhanced = extractLineParagraphs(tesseract, preprocessed, pageIndexZeroBased + 1);

        List<Paragraph> selected = scoreParagraphs(enhanced) >= scoreParagraphs(primary) ? enhanced : primary;

        final float splitX = estimateColumnSplitX(selected);
        selected.sort(Comparator
                .comparing(Paragraph::getPage)
                .thenComparingInt(p -> columnIndex(p, splitX))
                .thenComparing(Paragraph::getY)
                .thenComparing(Paragraph::getX));

        return selected;
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
            float x = bbox != null ? bbox.x : 0;
            float y = bbox != null ? bbox.y : 0;
            float fontSize = bbox != null ? Math.max(8f, bbox.height * 0.7f) : 10f;

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
        if (!path.getFileName().toString().equalsIgnoreCase("tessdata")) {
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

    private float estimateColumnSplitX(List<Paragraph> blocks) {
        if (blocks.isEmpty()) {
            return Float.NaN;
        }

        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        for (Paragraph p : blocks) {
            minX = Math.min(minX, p.getX());
            maxX = Math.max(maxX, p.getX());
        }

        if ((maxX - minX) < 260f) {
            return Float.NaN;
        }

        float splitX = (minX + maxX) / 2f;
        long left = blocks.stream().filter(p -> p.getX() < splitX).count();
        long right = blocks.size() - left;

        if (left < Math.max(2, blocks.size() / 5) || right < Math.max(2, blocks.size() / 5)) {
            return Float.NaN;
        }

        return splitX;
    }

    private Map<Integer, Float> estimateSplitByPage(List<Paragraph> paragraphs) {
        Map<Integer, List<Paragraph>> byPage = new HashMap<>();
        for (Paragraph p : paragraphs) {
            byPage.computeIfAbsent(p.getPage(), ignored -> new ArrayList<>()).add(p);
        }

        Map<Integer, Float> splitByPage = new HashMap<>();
        for (Map.Entry<Integer, List<Paragraph>> entry : byPage.entrySet()) {
            splitByPage.put(entry.getKey(), estimateColumnSplitX(entry.getValue()));
        }
        return splitByPage;
    }

    private int columnIndex(Paragraph p, float splitX) {
        if (Float.isNaN(splitX)) {
            return 0;
        }
        return p.getX() < splitX ? 0 : 1;
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

    private record PageImageRef(int pageIndex, BufferedImage image) {
    }
}
