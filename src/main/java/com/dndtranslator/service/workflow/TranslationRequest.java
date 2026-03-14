package com.dndtranslator.service.workflow;

import java.io.File;

public record TranslationRequest(File pdfFile, String targetLanguage) {
}

