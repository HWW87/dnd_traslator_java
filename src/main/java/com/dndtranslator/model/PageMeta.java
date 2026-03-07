package com.dndtranslator.model;

public class PageMeta {
    private final float width;
    private final float height;
    private final float leftMargin;
    private final float topMargin;
    private final int columnCount;
    private final String mainFont;
    private final float avgFontSize;
    private final float splitX;

    public PageMeta(float width, float height, float leftMargin, float topMargin, int columnCount, String mainFont, float avgFontSize) {
        this(width, height, leftMargin, topMargin, columnCount, mainFont, avgFontSize, Float.NaN);
    }

    public PageMeta(float width, float height, float leftMargin, float topMargin, int columnCount, String mainFont, float avgFontSize, float splitX) {
        this.width = width;
        this.height = height;
        this.leftMargin = leftMargin;
        this.topMargin = topMargin;
        this.columnCount = columnCount;
        this.mainFont = mainFont;
        this.avgFontSize = avgFontSize;
        this.splitX = splitX;
    }

    public float getWidth() { return width; }
    public float getHeight() { return height; }
    public float getLeftMargin() { return leftMargin; }
    public float getTopMargin() { return topMargin; }
    public int getColumnCount() { return columnCount; }
    public String getMainFont() { return mainFont; }
    public float getAvgFontSize() { return avgFontSize; }
    public float getSplitX() { return splitX; }
}
