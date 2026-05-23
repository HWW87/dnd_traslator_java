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
  - Orquestador unico de la ejecucion de traduccion.
  - Encadena extraccion, decision OCR, preparacion de unidades, traduccion, checkpoint y rebuild.
- `TranslationCoordinatorRuntimeWiring`
  - Borde de composicion runtime.
  - Resuelve provider con `TranslationProviderFactory` y construye dependencias concretas.
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
  - Servicio provider-agnostic basado en `TranslationProvider`.
  - Traduccion por texto y ruta canonica `translateUnit`.
  - Prompting unit-aware, retry policy y validacion/sanitizacion de salida.
  - Cache en SQLite (`translations.db`).
- `ParagraphTranslationExecutor`
  - Componente legacy mantenido por compatibilidad.
  - No es la ruta principal de orquestacion actual.
## Reconstruccion PDF
- `PdfRebuilderService`
  - Genera PDF de salida preservando layout base.
  - Gestiona fuentes y fallback CJK.
  - Aplica sanitizacion por glifo.
## Modelos de dominio
- `TranslationUnit`
  - Unidad canonica de trabajo en runtime (tipo, estado, retry, metadata).
- `Paragraph`
  - Artefacto de extraccion y correlacion visual para rebuild.
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
