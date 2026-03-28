package com.dndtranslator.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

public class FontResolver {

    private static final Logger logger = LoggerFactory.getLogger(FontResolver.class);

    static final String CJK_FONT_ENV_VAR = "DND_CJK_FONT_PATH";
    static final String CJK_RESOURCE_PATH = "/fonts/NotoSansCJKsc-Regular.otf";
    static final String WINDOWS_FONTS_DIR = "C:/Windows/Fonts";

    private static final String[] CJK_FONT_KEYWORDS = new String[]{
            "notosanscjk", "sourcehansans", "simhei", "simsun", "simkai",
            "msyh", "msjh", "meiryo", "msgothic", "yu gothic", "malgun",
            "mingliu", "pmingliu", "batang", "gulim", "dotum", "deng"
    };

    private final Function<String, String> envReader;
    private final File windowsFontsDir;

    public FontResolver() {
        this(System::getenv, new File(WINDOWS_FONTS_DIR));
    }

    FontResolver(Function<String, String> envReader, File windowsFontsDir) {
        this.envReader = envReader;
        this.windowsFontsDir = windowsFontsDir;
    }

    public PDType0Font resolveCjkFont(PDDocument doc, Class<?> resourceOwner) {
        try (InputStream cjkStream = resourceOwner.getResourceAsStream(CJK_RESOURCE_PATH)) {
            if (cjkStream != null) {
                PDType0Font resourceFont = PDType0Font.load(doc, cjkStream);
                logger.info("Usando fuente CJK embebida: {}", CJK_RESOURCE_PATH);
                return resourceFont;
            }
        } catch (IOException e) {
            logger.warn("No se pudo cargar fuente CJK embebida: {}", e.getMessage());
        }

        String customPath = envReader.apply(CJK_FONT_ENV_VAR);
        PDType0Font custom = tryLoadFontFile(doc, customPath, "entorno");
        if (custom != null) {
            return custom;
        }

        for (String candidate : findWindowsCjkCandidates()) {
            PDType0Font systemFont = tryLoadFontFile(doc, candidate, "sistema");
            if (systemFont != null) {
                return systemFont;
            }
        }

        return null;
    }

    List<String> findWindowsCjkCandidates() {
        List<String> candidates = new ArrayList<>();

        String[] fixedCandidates = new String[]{
                "C:/Windows/Fonts/simhei.ttf",
                "C:/Windows/Fonts/simsun.ttf",
                "C:/Windows/Fonts/simkai.ttf",
                "C:/Windows/Fonts/msyh.ttf",
                "C:/Windows/Fonts/meiryo.ttf",
                "C:/Windows/Fonts/malgun.ttf",
                "C:/Windows/Fonts/arialuni.ttf",
                "C:/Windows/Fonts/NotoSansCJKsc-Regular.otf"
        };
        for (String candidate : fixedCandidates) {
            candidates.add(candidate);
        }

        if (!windowsFontsDir.isDirectory()) {
            return candidates;
        }

        File[] dynamicCandidates = windowsFontsDir.listFiles((dir, name) -> {
            String lower = name.toLowerCase(Locale.ROOT);
            if (!isSupportedFontExtension(lower)) {
                return false;
            }
            for (String keyword : CJK_FONT_KEYWORDS) {
                if (lower.contains(keyword)) {
                    return true;
                }
            }
            return false;
        });

        if (dynamicCandidates != null) {
            for (File file : dynamicCandidates) {
                candidates.add(file.getAbsolutePath());
            }
        }

        return candidates;
    }

    private PDType0Font tryLoadFontFile(PDDocument doc, String filePath, String sourceLabel) {
        if (filePath == null || filePath.isBlank()) {
            return null;
        }

        File file = new File(filePath.trim());
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        if (!isSupportedFontExtension(file.getName())) {
            return null;
        }

        try {
            PDType0Font loaded = PDType0Font.load(doc, file);
            logger.info("Usando fuente CJK ({}): {}", sourceLabel, file.getAbsolutePath());
            return loaded;
        } catch (IOException e) {
            return null;
        }
    }

    boolean isSupportedFontExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(".ttf") || lower.endsWith(".otf");
    }
}

