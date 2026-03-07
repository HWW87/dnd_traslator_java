package com.dndtranslator.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;

import java.io.File;
import java.io.IOException;

public class FontLoader {

    public static PDType0Font loadDefaultFont(PDDocument doc) {
        try {
            File fontFile = new File("fonts/DejaVuSans.ttf");
            if (!fontFile.exists()) {
                System.err.println("⚠️ Fuente DejaVuSans.ttf no encontrada, usando Helvetica.");
                return PDType0Font.load(doc, new File("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"));
            }
            return PDType0Font.load(doc, fontFile);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo cargar la fuente TTF: " + e.getMessage(), e);
        }
    }
}
