package com.dndtranslator.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextLayoutEngineTest {

    @Test
    void wrapTextSplitsLinesByWidth() throws Exception {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            PDType0Font font = PDType0Font.load(document, fontStream);
            TextLayoutEngine engine = new TextLayoutEngine();

            List<String> lines = engine.wrapText("uno dos tres cuatro cinco seis", font, 11f, 70f);

            assertTrue(lines.size() > 1);
            for (String line : lines) {
                assertTrue(engine.measureTextWidth(line, font, 11f) <= 72f);
            }
        }
    }

    @Test
    void reducesFontSizeWhenTextCannotFitAtDefaultSize() throws Exception {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            PDType0Font font = PDType0Font.load(document, fontStream);
            TextLayoutEngine engine = new TextLayoutEngine();

            LayoutBox narrowBox = new LayoutBox(20f, 20f, 85f, 40f);
            try (PDPageContentStream stream = new PDPageContentStream(document, new PDPage(PDRectangle.LETTER))) {
                TextLayoutEngine.BoxRenderResult result = engine.renderTextInBox(
                        stream,
                        narrowBox,
                        "texto largo para validar ajuste dinamico de tamano de fuente",
                        font
                );

                assertTrue(result.usedFontSize() <= 11f);
                assertTrue(result.usedFontSize() >= 8f);
            }
        }
    }

    @Test
    void overflowsToNextBoxWhenCurrentBoxIsFull() throws Exception {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            PDType0Font font = PDType0Font.load(document, fontStream);
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            TextLayoutEngine engine = new TextLayoutEngine();
            List<LayoutBox> boxes = List.of(
                    new LayoutBox(20f, 20f, 80f, 24f),
                    new LayoutBox(20f, 55f, 80f, 24f)
            );

            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                TextLayoutEngine.RenderResult result = engine.renderText(
                        stream,
                        boxes,
                        "texto muy extenso para forzar overflow real incluso despues de consumir dos cajas de layout estrechas",
                        font,
                        22f,
                        22f
                );

                assertFalse(result.remainingText().isBlank());
                assertTrue(result.wroteAny());
            }
        }
    }

    @Test
    void wrapTextHardWrapsSingleOverlongToken() throws Exception {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            PDType0Font font = PDType0Font.load(document, fontStream);
            TextLayoutEngine engine = new TextLayoutEngine();

            List<String> lines = engine.wrapText("supercalifragilisticoespialidoso", font, 11f, 50f);

            assertTrue(lines.size() > 1);
            for (String line : lines) {
                assertTrue(engine.measureTextWidth(line, font, 11f) <= 52f);
            }
            assertEquals("supercalifragilisticoespialidoso", String.join("", lines));
        }
    }

    @Test
    void wrapTextKeepsNormalWordWrappingBehavior() throws Exception {
        try (PDDocument document = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            PDType0Font font = PDType0Font.load(document, fontStream);
            TextLayoutEngine engine = new TextLayoutEngine();

            List<String> lines = engine.wrapText("uno dos tres", font, 11f, 200f);

            assertEquals(1, lines.size());
            assertEquals("uno dos tres", lines.get(0));
        }
    }
}

