package com.dndtranslator.service;

import java.util.List;

public record PageLayout(List<LayoutBox> textBoxes, List<BlockedRegion> blockedRegions) {
}

