package com.dndtranslator.service;

import com.dndtranslator.model.PageMeta;
import com.dndtranslator.model.Paragraph;

import java.util.List;

public class PageRenderContext {

    private final int pageNumber;
    private final PageMeta pageMeta;
    private final List<PdfImagePlacement> pageImages;
    private final List<Paragraph> paragraphs;
    private final PageType pageType;
    private PageLayout pageLayout;

    public PageRenderContext(
            int pageNumber,
            PageMeta pageMeta,
            List<PdfImagePlacement> pageImages,
            List<Paragraph> paragraphs,
            PageType pageType
    ) {
        this.pageNumber = pageNumber;
        this.pageMeta = pageMeta;
        this.pageImages = pageImages;
        this.paragraphs = paragraphs;
        this.pageType = pageType;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public PageMeta getPageMeta() {
        return pageMeta;
    }

    public List<PdfImagePlacement> getPageImages() {
        return pageImages;
    }

    public List<Paragraph> getParagraphs() {
        return paragraphs;
    }

    public PageType getPageType() {
        return pageType;
    }

    public PageLayout getPageLayout() {
        return pageLayout;
    }

    public void setPageLayout(PageLayout pageLayout) {
        this.pageLayout = pageLayout;
    }
}

