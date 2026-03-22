package com.dndtranslator.service;

import org.apache.pdfbox.contentstream.PDFStreamEngine;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.contentstream.operator.state.Concatenate;
import org.apache.pdfbox.contentstream.operator.state.Restore;
import org.apache.pdfbox.contentstream.operator.state.Save;
import org.apache.pdfbox.contentstream.operator.state.SetGraphicsStateParameters;
import org.apache.pdfbox.contentstream.operator.state.SetMatrix;
import org.apache.pdfbox.contentstream.operator.DrawObject;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.util.Matrix;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PdfImageExtractor {

    private static final Logger logger = LoggerFactory.getLogger(PdfImageExtractor.class);

    public Map<Integer, List<PdfImagePlacement>> extract(String pdfPath) throws IOException {
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            return extract(document);
        }
    }

    public Map<Integer, List<PdfImagePlacement>> extract(PDDocument document) {
        if (document == null) {
            return Collections.emptyMap();
        }

        Map<Integer, List<PdfImagePlacement>> placementsByPage = new HashMap<>();
        for (int index = 0; index < document.getNumberOfPages(); index++) {
            int pageNumber = index + 1;
            PDPage page = document.getPage(index);
            PDRectangle mediaBox = page.getMediaBox();

            PageImageCollector collector = new PageImageCollector(pageNumber, mediaBox);
            try {
                collector.processPage(page);
            } catch (IOException e) {
                logger.warn("No se pudo analizar content stream de pagina {} para imagenes: {}", pageNumber, e.getMessage());
            }

            List<PdfImagePlacement> placements = new ArrayList<>(collector.getPlacements());
            Set<COSBase> seenImages = new HashSet<>(collector.getSeenImages());
            appendFallbackPlacements(page.getResources(), pageNumber, mediaBox, placements, seenImages);
            placementsByPage.put(pageNumber, List.copyOf(placements));
        }

        return placementsByPage;
    }

    private void appendFallbackPlacements(
            PDResources resources,
            int pageNumber,
            PDRectangle mediaBox,
            List<PdfImagePlacement> placements,
            Set<COSBase> seenImages
    ) {
        if (resources == null) {
            return;
        }

        List<PDImageXObject> fallbackImages = new ArrayList<>();
        collectImagesFromResources(resources, seenImages, fallbackImages, new HashSet<>());
        for (int i = 0; i < fallbackImages.size(); i++) {
            PDImageXObject image = fallbackImages.get(i);
            try {
                BufferedImage bufferedImage = image.getImage();
                if (bufferedImage == null) {
                    continue;
                }
                placements.add(createFallbackPlacement(pageNumber, mediaBox, bufferedImage, image.toString(), i));
            } catch (IOException e) {
                logger.warn("No se pudo crear fallback de imagen en pagina {}: {}", pageNumber, e.getMessage());
            }
        }
    }

    private void collectImagesFromResources(
            PDResources resources,
            Set<COSBase> seenImages,
            List<PDImageXObject> images,
            Set<COSBase> visitedForms
    ) {
        if (resources == null) {
            return;
        }

        for (COSName name : resources.getXObjectNames()) {
            try {
                PDXObject xObject = resources.getXObject(name);
                if (xObject instanceof PDImageXObject image) {
                    COSBase imageKey = image.getCOSObject();
                    if (!seenImages.contains(imageKey)) {
                        seenImages.add(imageKey);
                        images.add(image);
                    }
                } else if (xObject instanceof PDFormXObject form) {
                    COSBase formKey = form.getCOSObject();
                    if (visitedForms.add(formKey)) {
                        collectImagesFromResources(form.getResources(), seenImages, images, visitedForms);
                    }
                }
            } catch (IOException e) {
                logger.warn("No se pudo recorrer recurso XObject {}: {}", name.getName(), e.getMessage());
            }
        }
    }

    private static PdfImagePlacement createFallbackPlacement(
            int pageNumber,
            PDRectangle mediaBox,
            BufferedImage image,
            String sourceName,
            int index
    ) {
        float maxWidth = mediaBox.getWidth() * 0.42f;
        float maxHeight = mediaBox.getHeight() * 0.28f;
        float imageWidth = Math.max(1f, image.getWidth());
        float imageHeight = Math.max(1f, image.getHeight());
        float scale = Math.min(maxWidth / imageWidth, maxHeight / imageHeight);
        scale = Math.max(0.1f, Math.min(scale, 1.0f));

        float width = imageWidth * scale;
        float height = imageHeight * scale;
        float margin = 24f;
        float gap = 12f;
        float x = Math.max(margin, mediaBox.getWidth() - margin - width);
        float y = Math.max(margin, mediaBox.getHeight() - margin - height - (index * (height + gap)));

        return new PdfImagePlacement(
                pageNumber,
                image,
                x,
                y,
                width,
                height,
                false,
                sourceName,
                "fallback-top-right-stack"
        );
    }

    private static class PageImageCollector extends PDFStreamEngine {

        private final int pageNumber;
        private final PDRectangle mediaBox;
        private final List<PdfImagePlacement> placements = new ArrayList<>();
        private final Set<COSBase> seenImages = new HashSet<>();

        private PageImageCollector(int pageNumber, PDRectangle mediaBox) {
            this.pageNumber = pageNumber;
            this.mediaBox = mediaBox;
            addOperator(new Concatenate());
            addOperator(new DrawObject());
            addOperator(new SetGraphicsStateParameters());
            addOperator(new Save());
            addOperator(new Restore());
            addOperator(new SetMatrix());
        }

        @Override
        protected void processOperator(Operator operator, List<COSBase> operands) throws IOException {
            if ("Do".equals(operator.getName()) && !operands.isEmpty() && operands.getFirst() instanceof COSName objectName) {
                PDXObject xObject = getResources().getXObject(objectName);
                if (xObject instanceof PDImageXObject image) {
                    registerImagePlacement(objectName.getName(), image);
                    return;
                }
                if (xObject instanceof PDFormXObject form) {
                    showForm(form);
                    return;
                }
            }
            super.processOperator(operator, operands);
        }

        private void registerImagePlacement(String sourceName, PDImageXObject image) {
            try {
                BufferedImage bufferedImage = image.getImage();
                if (bufferedImage == null) {
                    return;
                }

                Matrix ctm = getGraphicsState().getCurrentTransformationMatrix();
                float[] bounds = computeBounds(ctm);
                COSBase imageKey = image.getCOSObject();
                seenImages.add(imageKey);

                if (bounds == null) {
                    placements.add(createFallbackPlacement(pageNumber, mediaBox, bufferedImage, sourceName, placements.size()));
                    return;
                }

                placements.add(new PdfImagePlacement(
                        pageNumber,
                        bufferedImage,
                        bounds[0],
                        bounds[1],
                        bounds[2],
                        bounds[3],
                        true,
                        sourceName,
                        "exact-bounding-box"
                ));
            } catch (IOException e) {
                logger.warn("No se pudo extraer imagen {} en pagina {}: {}", sourceName, pageNumber, e.getMessage());
            }
        }

        private float[] computeBounds(Matrix ctm) {
            if (ctm == null) {
                return null;
            }

            Point2D.Float p0 = ctm.transformPoint(0, 0);
            Point2D.Float p1 = ctm.transformPoint(1, 0);
            Point2D.Float p2 = ctm.transformPoint(0, 1);
            Point2D.Float p3 = ctm.transformPoint(1, 1);

            float minX = Math.min(Math.min(p0.x, p1.x), Math.min(p2.x, p3.x));
            float maxX = Math.max(Math.max(p0.x, p1.x), Math.max(p2.x, p3.x));
            float minY = Math.min(Math.min(p0.y, p1.y), Math.min(p2.y, p3.y));
            float maxY = Math.max(Math.max(p0.y, p1.y), Math.max(p2.y, p3.y));
            float width = maxX - minX;
            float height = maxY - minY;

            if (!Float.isFinite(minX) || !Float.isFinite(minY) || !Float.isFinite(width) || !Float.isFinite(height)) {
                return null;
            }
            if (width <= 0.5f || height <= 0.5f) {
                return null;
            }

            float x = clamp(minX, 0f, mediaBox.getWidth());
            float y = clamp(minY, 0f, mediaBox.getHeight());
            float clampedWidth = Math.min(width, mediaBox.getWidth() - x);
            float clampedHeight = Math.min(height, mediaBox.getHeight() - y);
            if (clampedWidth <= 0.5f || clampedHeight <= 0.5f) {
                return null;
            }
            return new float[]{x, y, clampedWidth, clampedHeight};
        }

        private static float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(value, max));
        }

        private List<PdfImagePlacement> getPlacements() {
            return placements;
        }

        private Set<COSBase> getSeenImages() {
            return seenImages;
        }
    }
}

