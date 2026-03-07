package com.dndtranslator.model;

public class Paragraph {
    private String fullText;
    private final int page;
    private final float x;
    private final float y;
    private final String fontName;
    private final float fontSize;
    private String translatedText = "";

    public Paragraph(String fullText, int page, float x, float y, String fontName, float fontSize) {
        this.fullText = fullText;
        this.page = page;
        this.x = x;
        this.y = y;
        this.fontName = fontName;
        this.fontSize = fontSize;
    }

    public void appendText(String more) {
        fullText += more;
    }

    public String getFullText() { return fullText; }
    public void setTranslatedText(String t) { this.translatedText = t; }
    public String getTranslatedText() { return translatedText; }

    public int getPage() { return page; }
    public float getX() { return x; }
    public float getY() { return y; }
    public String getFontName() { return fontName; }
    public float getFontSize() { return fontSize; }
}
