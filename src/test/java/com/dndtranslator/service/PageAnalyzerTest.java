package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PageAnalyzerTest {

    @Test
    void computesExpectedSignalsFromSimpleInputs() {
        PageAnalyzer analyzer = new PageAnalyzer();

        PageMeta meta = new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f);
        List<Paragraph> paragraphs = List.of(
                new Paragraph("Map of the frontier zone", 1, 40f, 700f, "Font", 12f),
                new Paragraph("Sector A 12", 1, 40f, 680f, "Font", 10f)
        );

        PdfImagePlacement image = new PdfImagePlacement(
                1,
                sampleImage(),
                60f,
                200f,
                420f,
                300f,
                true,
                "img-1",
                "exact-bounding-box"
        );

        PageAnalysisData data = analyzer.analyze(1, meta, paragraphs, List.of(image));

        assertTrue(data.imageCount() == 1);
        assertTrue(data.estimatedImageAreaRatio() > 0.20f);
        assertTrue(data.hasMapLikeKeywords());
        assertTrue(data.wordCount() > 0);
        assertTrue(data.lineCount() >= 2);
    }

    private BufferedImage sampleImage() {
        BufferedImage image = new BufferedImage(120, 80, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < image.getWidth(); x++) {
            for (int y = 0; y < image.getHeight(); y++) {
                image.setRGB(x, y, Color.GREEN.getRGB());
            }
        }
        return image;
    }
}

