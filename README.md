# D&D PDF Translator

A JavaFX desktop application that translates D&D (and other RPG) PDF documents using a local Ollama LLM instance.  
It preserves the original page layout and supports a configurable D&D glossary to keep game-specific terms accurate.

## Prerequisites

| Requirement | Version |
|-------------|---------|
| JDK | 17 or later |
| Maven | 3.6 or later |
| [Ollama](https://ollama.com/) | running locally on `http://localhost:11434` |

> **Note:** A JavaFX-capable JDK is required (e.g. [Temurin](https://adoptium.net/) or any distribution that includes JavaFX, or set `JAVA_HOME` to a JDK and let the JavaFX Maven plugin handle it).

## Build

```bash
mvn compile
```

## Run tests

```bash
mvn test
```

## Run the application

```bash
mvn javafx:run
```

This launches the D&D Translator desktop window.  
Select a PDF file, then click **Translate** to start the translation pipeline.

## How it works

1. **PDF extraction** – text is extracted from the PDF preserving layout metadata.  
2. **OCR fallback** – if embedded text quality is poor, Tesseract OCR is used automatically.  
3. **D&D glossary** – game-specific terms (e.g. *Armor Class*, *Hit Points*) are replaced with placeholder tokens before translation and restored with their Spanish equivalents afterwards.  
   Glossary file: `src/main/resources/glossary/dnd-glossary.json`  
4. **Translation** – paragraphs are sent to Ollama in parallel.  
5. **PDF rebuild** – the translated text is written back into a new PDF that mirrors the original layout.  
   Output file: `<original-name>_translated_layout.pdf` (next to the source PDF).

## Project structure

```
src/
  main/java/com/dndtranslator/
    TranslatorUI.java               # JavaFX entry point
    service/workflow/               # Core translation pipeline
      TranslationCoordinatorService # Orchestrates the full pipeline
      ParagraphTranslationExecutor  # Parallel paragraph translation
      GlossaryService               # D&D glossary pre/post processing
      OcrDecisionService            # Decides embedded vs OCR extraction
      TextSanitizer                 # Cleans raw text before translation
    service/
      PdfExtractorService           # Embedded text extraction (PDFBox)
      PdfToParagraphService         # OCR-based extraction (Tess4J)
      PdfRebuilderService           # Reconstructs translated PDF
      TranslatorService             # Ollama HTTP client
  main/resources/
    glossary/dnd-glossary.json      # Configurable D&D term glossary
  test/java/com/dndtranslator/
    service/workflow/               # Unit and integration tests
```

## Customising the glossary

Edit `src/main/resources/glossary/dnd-glossary.json`:

```json
[
  { "source": "Armor Class", "target": "Clase de Armadura" },
  { "source": "Hit Points",  "target": "Puntos de Golpe"   },
  { "source": "Dungeon Master", "preserve": true }
]
```

- `"target"` – Spanish (or any language) replacement used after translation.  
- `"preserve": true` – keeps the original term untouched (no translation).
