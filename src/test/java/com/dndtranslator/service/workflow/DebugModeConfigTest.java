package com.dndtranslator.service.workflow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugModeConfigTest {

    @Test
    void enablesDebugForTruthyValues() {
        assertTrue(DebugModeConfig.isEnabled("true"));
        assertTrue(DebugModeConfig.isEnabled("1"));
        assertTrue(DebugModeConfig.isEnabled("yes"));
        assertTrue(DebugModeConfig.isEnabled("ON"));
    }

    @Test
    void disablesDebugForNullOrFalsyValues() {
        assertFalse(DebugModeConfig.isEnabled(null));
        assertFalse(DebugModeConfig.isEnabled(""));
        assertFalse(DebugModeConfig.isEnabled("false"));
        assertFalse(DebugModeConfig.isEnabled("0"));
    }
}

