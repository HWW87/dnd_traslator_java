package com.dndtranslator.model;

public class TextBlock {
    private final String text;
    private final int page;
    private final float x, y, width, height, fontSize;
    private final String fontName;

    public TextBlock(String text, int page, float x, float y, float width, float height, float fontSize, String fontName) {
        this.text = text;
        this.page = page;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.fontSize = fontSize;
        this.fontName = fontName;
    }

    public String getText() { return text; }
    public int getPage() { return page; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public float getFontSize() { return fontSize; }
    public String getFontName() { return fontName; }
}
