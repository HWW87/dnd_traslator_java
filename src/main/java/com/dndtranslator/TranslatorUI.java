package com.dndtranslator;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import com.dndtranslator.service.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 🎨 Interfaz JavaFX principal del traductor D&D.
 * Traduce PDFs usando Ollama y reconstruye el resultado respetando layout original.
 */
public class TranslatorUI extends Application {

    private final TranslatorService translator = new TranslatorService();
    private final PdfRebuilderService rebuilder = new PdfRebuilderService();

    private TextArea logArea;
    private ProgressBar progressBar;
    private Button pauseButton;

    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean stopped = new AtomicBoolean(false);

    private static final int MIN_TEXT_CHARS_PER_PAGE = 140;
    private static final double MAX_NOISY_RATIO_FOR_EMBEDDED = 0.28d;
    private static final double MAX_SUSPICIOUS_RATIO_FOR_EMBEDDED = 0.035d;
    private static final int MIN_SUSPICIOUS_CHARS_PER_PAGE = 10;


    @Override
    public void start(Stage stage) {
        stage.setTitle("🧙 D&D and Others PDF Translator (Ollama + JavaFX)");

        Button fileButton = new Button("📂 Seleccionar PDF");
        Button translateButton = new Button("⚙️ Traducir");
        pauseButton = new Button("⏸️ Pausar");
        Button stopButton = new Button("⛔ Detener");
        Button exitButton = new Button("🚪 Salir");

        translateButton.setDisable(true);
        progressBar = new ProgressBar(0);
        logArea = new TextArea();
        logArea.setEditable(false);

        fileButton.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Archivos PDF", "*.pdf")
            );
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                log("📄 Archivo seleccionado: " + file.getAbsolutePath());
                translateButton.setDisable(false);
                translateButton.setOnAction(ev -> processPDF(file));
            }
        });

        pauseButton.setOnAction(e -> {
            boolean isPaused = paused.get();
            paused.set(!isPaused);
            pauseButton.setText(isPaused ? "⏸️ Pausar" : "▶️ Reanudar");
            log(isPaused ? "▶️ Reanudando proceso..." : "⏸️ Pausando traducción...");
        });

        stopButton.setOnAction(e -> {
            stopped.set(true);
            paused.set(false);
            pauseButton.setText("⏸️ Pausar");
            log("🛑 Detención solicitada. Esperando cierre seguro...");
        });

        exitButton.setOnAction(e -> {
            translator.shutdown();
            Platform.exit();
            System.exit(0);
        });

        VBox layout = new VBox(10,
                new HBox(10, fileButton, translateButton, pauseButton, stopButton, exitButton),
                progressBar, logArea);
        layout.setPadding(new Insets(15));

        Scene scene = new Scene(layout, 850, 520);
        stage.setScene(scene);
        stage.show();
    }

    /**
     * 🔄 Proceso principal de traducción y reconstrucción PDF.
     */
    private void processPDF(File pdfFile) {
        if (pdfFile == null || !pdfFile.exists() || !pdfFile.canRead()) {
            log("❌ El archivo seleccionado no existe o no se puede leer.");
            return;
        }

        paused.set(false);
        stopped.set(false);
        pauseButton.setText("⏸️ Pausar");

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                try {
                    log("📐 Analizando maquetación y extrayendo texto...");

                    PdfExtractorService extractor = new PdfExtractorService();
                    List<Paragraph> paragraphs = extractor.extractParagraphs(pdfFile.getAbsolutePath());
                    Map<Integer, PageMeta> layoutInfo = extractor.getLayoutInfo();

                    boolean poorEmbeddedQuality = shouldUseOcrFallback(paragraphs, layoutInfo);
                    if (paragraphs.isEmpty() || poorEmbeddedQuality) {
                        if (paragraphs.isEmpty()) {
                            log("🧠 No se detecto texto embebido. Activando OCR embebido...");
                        } else {
                            log("🧠 Texto embebido detectado pero con calidad baja. Activando OCR embebido...");
                        }
                        PdfToParagraphService ocrExtractor = new PdfToParagraphService();
                        paragraphs = ocrExtractor.extractParagraphsFromPdf(pdfFile);
                    }

                    int total = paragraphs.size();
                    if (total == 0) {
                        log("❌ No se encontraron párrafos para traducir.");
                        return null;
                    }

                    log("📄 Párrafos detectados: " + total);

                    int workers = Math.max(1, Runtime.getRuntime().availableProcessors());
                    log("⚙️ Traducción paralela habilitada con " + workers + " hilos.");

                    ExecutorService translationPool = Executors.newFixedThreadPool(workers);
                    CompletionService<TranslationResult> completionService = new ExecutorCompletionService<>(translationPool);

                    int submitted = 0;
                    int completed = 0;
                    int inFlight = 0;

                    try {
                        while (completed < total) {
                            if (stopped.get()) {
                                log("⛔ Proceso detenido por el usuario.");
                                return null;
                            }

                            while (!paused.get() && submitted < total && inFlight < workers) {
                                final int idx = submitted;
                                final Paragraph paragraph = paragraphs.get(idx);
                                completionService.submit(() -> {
                                    String sourceText = paragraph.getFullText();
                                    String translated = translator.translate(
                                            sourceText.replaceAll("[^\\p{L}\\p{N}\\s.,;:!?¿¡()\\[\\]\\-]", ""),
                                            "Spanish"
                                    );
                                    return new TranslationResult(idx, translated);
                                });
                                submitted++;
                                inFlight++;
                            }

                            if (paused.get()) {
                                if (inFlight == 0 && submitted < total) {
                                    Thread.sleep(200);
                                }
                                continue;
                            }

                            Future<TranslationResult> done = completionService.poll(300, TimeUnit.MILLISECONDS);
                            if (done == null) {
                                continue;
                            }

                            TranslationResult result = done.get();
                            inFlight--;
                            completed++;

                            paragraphs.get(result.index()).setTranslatedText(result.text());
                            updateProgress(completed, total);
                            log("✅ Traducido párrafo " + completed + "/" + total);
                        }
                    } finally {
                        translationPool.shutdownNow();
                    }

                    log("🧾 Reconstruyendo PDF con layout original...");
                    rebuilder.rebuild(pdfFile.getAbsolutePath(), paragraphs, layoutInfo);
                    log("🎉 Traducción completa con maquetación preservada.");

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log("⛔ Proceso interrumpido.");
                } catch (Exception e) {
                    log("❌ Error: " + e.getMessage());
                }
                return null;
            }
        };

        progressBar.progressProperty().unbind();
        progressBar.progressProperty().bind(task.progressProperty());
        Thread worker = new Thread(task, "pdf-translation-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private record TranslationResult(int index, String text) {
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));

    }

    private boolean shouldUseOcrFallback(List<Paragraph> paragraphs, Map<Integer, PageMeta> layoutInfo) {
        if (paragraphs == null || paragraphs.isEmpty()) {
            return true;
        }

        int pages = Math.max(1, layoutInfo.isEmpty()
                ? paragraphs.stream().mapToInt(Paragraph::getPage).max().orElse(1)
                : layoutInfo.size());

        int totalChars = paragraphs.stream().mapToInt(p -> p.getFullText().length()).sum();
        int expectedMinChars = pages * MIN_TEXT_CHARS_PER_PAGE;
        if (totalChars < expectedMinChars) {
            return true;
        }

        double noisyRatio = computeNoisyRatio(paragraphs);
        if (noisyRatio > MAX_NOISY_RATIO_FOR_EMBEDDED) {
            return true;
        }

        double suspiciousRatio = computeSuspiciousRatio(paragraphs);
        if (suspiciousRatio > MAX_SUSPICIOUS_RATIO_FOR_EMBEDDED) {
            return true;
        }

        int suspiciousChars = countSuspiciousChars(paragraphs);
        return suspiciousChars >= pages * MIN_SUSPICIOUS_CHARS_PER_PAGE;
    }

    private double computeNoisyRatio(List<Paragraph> paragraphs) {
        long totalChars = 0;
        long noisyChars = 0;

        for (Paragraph paragraph : paragraphs) {
            String text = paragraph.getFullText();
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
            return 1d;
        }
        return (double) noisyChars / (double) totalChars;
    }

    private double computeSuspiciousRatio(List<Paragraph> paragraphs) {
        long totalChars = 0;
        long suspiciousChars = 0;

        for (Paragraph paragraph : paragraphs) {
            String text = paragraph.getFullText();
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
            return 1d;
        }
        return (double) suspiciousChars / (double) totalChars;
    }

    private int countSuspiciousChars(List<Paragraph> paragraphs) {
        int suspiciousChars = 0;
        for (Paragraph paragraph : paragraphs) {
            String text = paragraph.getFullText();
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (Character.isWhitespace(c)) {
                    continue;
                }
                if (c == '?' || c == '\u000B' || c == '\u000C' || Character.isISOControl(c)) {
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
        return suspiciousChars;
    }

    public static void main(String[] args) {
        launch();
    }
}
