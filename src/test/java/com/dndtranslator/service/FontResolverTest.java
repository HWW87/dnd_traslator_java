package com.dndtranslator.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FontResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void supportsOnlyTtfAndOtfExtensions() {
        FontResolver resolver = new FontResolver(name -> null, tempDir.toFile());

        assertTrue(resolver.isSupportedFontExtension("font.ttf"));
        assertTrue(resolver.isSupportedFontExtension("font.OTF"));
        assertFalse(resolver.isSupportedFontExtension("font.txt"));
    }

    @Test
    void findsDynamicWindowsCandidatesByKeywordAndFontExtension() throws Exception {
        Path keywordFont = tempDir.resolve("MyNotoSansCJK-Regular.ttf");
        Path nonKeywordFont = tempDir.resolve("random-font.ttf");
        Path keywordNonFont = tempDir.resolve("simhei.txt");

        Files.writeString(keywordFont, "dummy");
        Files.writeString(nonKeywordFont, "dummy");
        Files.writeString(keywordNonFont, "dummy");

        FontResolver resolver = new FontResolver(name -> null, tempDir.toFile());
        List<String> candidates = resolver.findWindowsCjkCandidates();

        assertTrue(candidates.stream().anyMatch(path -> path.equals(keywordFont.toAbsolutePath().toString())));
        assertFalse(candidates.stream().anyMatch(path -> path.equals(nonKeywordFont.toAbsolutePath().toString())));
        assertFalse(candidates.stream().anyMatch(path -> path.equals(keywordNonFont.toAbsolutePath().toString())));
    }

    @Test
    void returnsNullWhenNoEmbeddedEnvOrSystemFontCanBeLoaded() throws Exception {
        FontResolver resolver = new FontResolver(name -> "", tempDir.toFile()) {
            @Override
            List<String> findWindowsCjkCandidates() {
                return List.of();
            }
        };

        try (PDDocument doc = new PDDocument()) {
            assertNull(resolver.resolveCjkFont(doc, FontResolverTest.class));
        }
    }
}

