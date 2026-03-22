# Modulos y servicios
## UI y control de tareas
- `src/main/java/com/dndtranslator/TranslatorUI.java`
  - UI principal JavaFX.
  - Gestiona seleccion de archivo, progreso y logs.
- `src/main/java/com/dndtranslator/service/workflow/TranslationTaskManager.java`
  - Crea y administra `Task<TranslationResult>`.
  - Maneja pausa/detencion con `AtomicBoolean`.
## Orquestacion de workflow
- `TranslationCoordinatorService`
  - Caso de uso central.
  - Encadena extraccion, decision OCR, traduccion y rebuild.
- `TranslationRequest` / `TranslationResult`
  - DTOs inmutables del flujo.
- `TranslationEventListener` / `TranslationProgress`
  - Contratos de eventos de log y progreso.
## Calidad de extraccion y limpieza de texto
- `OcrDecisionService`
  - Reglas para determinar si OCR fallback es necesario.
- `ExtractionQualityEvaluator`
  - Adaptador legacy que delega en `OcrDecisionService`.
- `TextSanitizer`
  - Normaliza texto y elimina ruido antes de traducir.
## Glosario de terminos DnD
- `GlossaryEntry`
  - Define termino origen, destino y politica de preservacion.
- `GlossaryService`
  - Carga `src/main/resources/glossary/dnd-glossary.json`.
  - Protege terminos con placeholders antes de traducir.
  - Restaura terminos al finalizar traduccion.
## Extraccion de texto
- `PdfExtractorService`
  - Extrae texto embebido con coordenadas.
  - Detecta paginas de 1 o 2 columnas.
- `PdfToParagraphService`
  - Fallback OCR con Tess4J.
  - Construye `Paragraph` + `PageMeta` desde imagen.
## Traduccion y cache
- `TranslatorService`
  - Cliente HTTP para Ollama (`/api/generate`, `/api/tags`).
  - Segmentacion de texto largo.
  - Reintentos de red y limpieza de salida.
  - Cache en SQLite (`translations.db`).
- `ParagraphTranslationExecutor`
  - Paraleliza traduccion por parrafo y publica progreso.
## Reconstruccion PDF
- `PdfRebuilderService`
  - Genera PDF de salida preservando layout base.
  - Gestiona fuentes y fallback CJK.
  - Aplica sanitizacion por glifo.
## Modelos de dominio
- `Paragraph`
  - Texto original, texto traducido, pagina, posicion y fuente.
- `PageMeta`
  - Ancho/alto pagina, margenes, columnas, `splitX`.
- `TextBlock`
  - Modelo legacy para traduccion por bloques.
## Herramientas y utilidades
- `ExtractionDiagnostics`
  - CLI para inspeccionar calidad de extraccion y columnas.
- `FontLoader`
  - Utilidad legacy para cargar fuente; no es la ruta principal actual.
- `TranslationCacheService`
  - Servicio de cache legacy no acoplado al flujo principal actual.
