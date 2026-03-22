package com.dndtranslator.service;

public record LayoutBox(float x, float y, float width, float height) {

    public float right() {
        return x + width;
    }

    public float top() {
        return y + height;
    }

    public boolean contains(float px, float py) {
        return px >= x && px <= right() && py >= y && py <= top();
    }
}

