package com.dndtranslator.service.workflow;

public record GlossaryEntry(String sourceTerm, String targetTerm, boolean preserveSource) {

    public GlossaryEntry {
        if (sourceTerm == null || sourceTerm.isBlank()) {
            throw new IllegalArgumentException("sourceTerm cannot be null or blank");
        }
    }

    public String outputTerm() {
        if (preserveSource || targetTerm == null || targetTerm.isBlank()) {
            return sourceTerm;
        }
        return targetTerm;
    }
}

