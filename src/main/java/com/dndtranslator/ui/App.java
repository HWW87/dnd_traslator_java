package com.dndtranslator.ui;

import javafx.application.Application;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.layout.VBox;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import java.io.File;

public class App extends Application {

    @Override
    public void start(Stage stage) {
        stage.setTitle("🧙‍♂️ D&D Translator JavaFX");

        Label label = new Label("Selecciona un PDF para traducir:");
        Button uploadBtn = new Button("📂 Elegir PDF");
        TextArea log = new TextArea();
        log.setPrefHeight(300);

        uploadBtn.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF files (*.pdf)", "*.pdf"));
            File file = chooser.showOpenDialog(stage);
            if (file != null) {
                log.appendText("📄 Traduciendo: " + file.getName() + "\\n");
                // Lógica: llamar a Main API o directo a servicios Java

            }
        });

        VBox root = new VBox(10, label, uploadBtn, log);
        Scene scene = new Scene(root, 500, 400);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}
