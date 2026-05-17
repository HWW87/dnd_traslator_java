package com.dndtranslator.service.regression;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;
import com.dndtranslator.service.PageType;
import com.dndtranslator.service.PdfImagePlacement;
import org.json.JSONArray;
import org.json.JSONObject;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PageCorpusLoader {

    private static final String BASE = "regression/page-corpus/v1/";

    List<RegressionCase> load() {
        JSONObject manifest = readJson(BASE + "manifest.json");
        JSONArray cases = manifest.getJSONArray("cases");

        List<RegressionCase> out = new ArrayList<>();
        for (int i = 0; i < cases.length(); i++) {
            String fileName = cases.getString(i);
            JSONObject root = readJson(BASE + "cases/" + fileName);
            out.add(parseCase(root));
        }
        return out;
    }

    private RegressionCase parseCase(JSONObject root) {
        String id = root.getString("id");
        JSONObject meta = root.getJSONObject("pageMeta");

        int pageNumber = meta.getInt("pageNumber");

        PageMeta pageMeta = new PageMeta(
                (float) meta.getDouble("width"),
                (float) meta.getDouble("height"),
                (float) meta.getDouble("leftMargin"),
                (float) meta.getDouble("topMargin"),
                1,
                meta.getString("fontName"),
                (float) meta.getDouble("fontSize")
        );

        List<Paragraph> paragraphs = new ArrayList<>();
        JSONArray paragraphArray = root.getJSONArray("paragraphs");
        for (int i = 0; i < paragraphArray.length(); i++) {
            JSONObject p = paragraphArray.getJSONObject(i);
            paragraphs.add(new Paragraph(
                    p.getString("text"),
                    pageNumber,
                    (float) p.getDouble("x"),
                    (float) p.getDouble("y"),
                    p.optString("fontName", "Font"),
                    (float) p.optDouble("fontSize", 12.0)
            ));
        }

        List<PdfImagePlacement> images = new ArrayList<>();
        JSONArray imageArray = root.getJSONArray("images");
        for (int i = 0; i < imageArray.length(); i++) {
            JSONObject image = imageArray.getJSONObject(i);
            images.add(new PdfImagePlacement(
                    pageNumber,
                    new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB),
                    (float) image.getDouble("x"),
                    (float) image.getDouble("y"),
                    (float) image.getDouble("width"),
                    (float) image.getDouble("height"),
                    true,
                    "fixture-" + id,
                    "fixture"
            ));
        }

        JSONObject expected = root.getJSONObject("expected");
        PageType expectedType = PageType.valueOf(expected.getString("pageType"));
        Map<String, Boolean> expectedSignals = new LinkedHashMap<>();
        JSONObject signals = expected.optJSONObject("signals");
        if (signals != null) {
            for (String key : signals.keySet()) {
                expectedSignals.put(key, signals.getBoolean(key));
            }
        }

        return new RegressionCase(id, pageNumber, pageMeta, paragraphs, images, expectedType, expectedSignals);
    }

    private JSONObject readJson(String resourcePath) {
        try (InputStream is = PageCorpusLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IllegalStateException("No se encontro recurso de regresion: " + resourcePath);
            }
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new JSONObject(text);
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo cargar recurso de regresion: " + resourcePath, e);
        }
    }

    record RegressionCase(
            String id,
            int pageNumber,
            PageMeta pageMeta,
            List<Paragraph> paragraphs,
            List<PdfImagePlacement> images,
            PageType expectedType,
            Map<String, Boolean> expectedSignals
    ) {
    }
}

