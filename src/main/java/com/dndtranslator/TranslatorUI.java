package com.dndtranslator;

import com.dndtranslator.service.workflow.TranslationCoordinatorService;
import com.dndtranslator.service.workflow.TranslationProgressListener;
import com.dndtranslator.service.workflow.TranslationRequest;
import com.dndtranslator.service.workflow.TranslationResult;
import com.dndtranslator.service.workflow.TranslationTaskManager;
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
import java.util.concurrent.CancellationException;

/**
 * 🎨 Interfaz JavaFX principal del traductor D&D.
 * Traduce PDFs usando Ollama y reconstruye el resultado respetando layout original.
 */
public class TranslatorUI extends Application {

    private final TranslationCoordinatorService translationCoordinator = new TranslationCoordinatorService();
    private final TranslationTaskManager taskManager = new TranslationTaskManager();

    private TextArea logArea;
    private ProgressBar progressBar;
    private Button pauseButton;


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
            boolean pausedNow = taskManager.togglePause();
            pauseButton.setText(pausedNow ? "▶️ Reanudar" : "⏸️ Pausar");
            log(pausedNow ? "⏸️ Pausando traducción..." : "▶️ Reanudando proceso...");
        });

        stopButton.setOnAction(e -> {
            taskManager.requestStop();
            pauseButton.setText("⏸️ Pausar");
            log("🛑 Detencion solicitada. Esperando cierre seguro...");
        });

        exitButton.setOnAction(e -> {
            translationCoordinator.shutdown();
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

        taskManager.resetControlFlags();
        pauseButton.setText("⏸️ Pausar");

        TranslationRequest request = new TranslationRequest(pdfFile, "Spanish");
        Task<TranslationResult> task = taskManager.start(request, translationCoordinator, new TranslationProgressListener() {
            @Override
            public void onLog(String message) {
                log(message);
            }
        });

        progressBar.progressProperty().unbind();
        progressBar.progressProperty().bind(task.progressProperty());

        Thread worker = new Thread(task, "pdf-translation-worker");
        worker.setDaemon(true);
        worker.start();

        task.setOnSucceeded(e -> {
            TranslationResult result = task.getValue();
            if (result != null) {
                log("📦 Archivo de salida: " + result.outputPdfPath());
            }
        });
        task.setOnCancelled(e -> log("⛔ Proceso cancelado."));
        task.setOnFailed(e -> {
            Throwable error = task.getException();
            if (error instanceof CancellationException) {
                log("⛔ Proceso cancelado.");
            } else if (error != null) {
                log("❌ Error: " + error.getMessage());
            } else {
                log("❌ Error desconocido durante la traducción.");
            }
        });
    }

    private void log(String msg) {
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    public static void main(String[] args) {
        launch();
    }
}
