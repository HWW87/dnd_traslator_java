package com.dndtranslator.service;

public record BlockedRegion(float x, float y, float width, float height) {

    public float right() {
        return x + width;
    }

    public float top() {
        return y + height;
    }

    public boolean intersects(LayoutBox box) {
        if (box == null) {
            return false;
        }
        return box.right() > x && box.x() < right() && box.top() > y && box.y() < top();
    }
}

