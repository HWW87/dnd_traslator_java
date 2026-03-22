package com.dndtranslator.service;

import java.awt.image.BufferedImage;

public record PdfImagePlacement(
        int pageNumber,
        BufferedImage image,
        float x,
        float y,
        float width,
        float height,
        boolean exactBounds,
        String sourceName,
        String placementStrategy
) {

    public boolean isRenderable() {
        return image != null && width > 0f && height > 0f;
    }
}

