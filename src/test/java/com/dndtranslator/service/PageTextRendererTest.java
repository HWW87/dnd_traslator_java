package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageTextRendererTest {

    @Test
    void writeParagraphsCollectsOverflowFromCarryOverAndParagraphs() throws Exception {
        StubTextLayoutEngine layoutEngine = new StubTextLayoutEngine();
        PageTextRenderer renderer = new PageTextRenderer(layoutEngine);

        PageLayout pageLayout = new PageLayout(
                List.of(new LayoutBox(24f, 24f, 300f, 400f)),
                List.of()
        );

        Paragraph paragraph = new Paragraph("original", 1, 40f, 120f, "Font", 12f);
        paragraph.setTranslatedText("overflow paragraph");

        try (PDDocument document = new PDDocument();
             InputStream fontStream = getClass().getResourceAsStream("/fonts/NotoSans-Regular.ttf")) {
            document.addPage(new PDPage());
            PDType0Font font = PDType0Font.load(document, fontStream);

            List<String> overflow;
            try (PDPageContentStream stream = new PDPageContentStream(document, document.getPage(0))) {
                overflow = renderer.writeParagraphs(
                        stream,
                        font,
                        new PageMeta(600f, 800f, 24f, 24f, 1, "Font", 12f),
                        List.of(paragraph),
                        pageLayout,
                        List.of("overflow carry")
                );
            }

            assertEquals(2, overflow.size());
            assertEquals("remaining-overflow carry", overflow.get(0));
            assertEquals("remaining-overflow paragraph", overflow.get(1));
            assertTrue(layoutEngine.capturedTexts.contains("overflow carry"));
            assertTrue(layoutEngine.capturedTexts.contains("overflow paragraph"));
        }
    }

    private static class StubTextLayoutEngine extends TextLayoutEngine {
        private final List<String> capturedTexts = new ArrayList<>();

        @Override
        public RenderResult renderText(
                PDPageContentStream stream,
                List<LayoutBox> boxes,
                String text,
                PDType0Font font,
                float preferredX,
                float preferredY
        ) {
            capturedTexts.add(text);
            if (text.startsWith("overflow")) {
                return new RenderResult("remaining-" + text, true, 11f);
            }
            return new RenderResult("", true, 11f);
        }
    }
}

