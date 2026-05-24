# Arquitectura

## Vista general

El proyecto sigue una arquitectura por capas:

- UI JavaFX: `com.dndtranslator.TranslatorUI`
- Orquestacion de flujo: `com.dndtranslator.service.workflow`
- Servicios de infraestructura: `com.dndtranslator.service`
- Modelos de dominio: `com.dndtranslator.model`
- Herramientas de diagnostico: `com.dndtranslator.tools`

## Entrada principal

- `TranslatorUI` gestiona eventos de interfaz (seleccionar PDF, pausar, detener).
- Crea una `TranslationRequest` y delega en `TranslationTaskManager`.
- `TranslationTaskManager` encapsula un `javafx.concurrent.Task` con control de pausa/detencion.

## Nucleo de aplicacion

`TranslationCoordinatorService` implementa el caso de uso principal:

1. Valida el request.
2. Extrae parrafos y metadatos de layout (texto embebido).
3. Decide si aplicar OCR fallback (`OcrDecisionService`).
4. Convierte parrafos a `TranslationUnit` y ejecuta traduccion secuencial unit-first.
5. Persiste/restaura progreso por unidad con `CheckpointStore`.
6. Sincroniza unidades traducidas con artefactos de rebuild y reconstruye PDF.
6. Retorna `TranslationResult`.

`TranslationCoordinatorRuntimeWiring` es el borde de composicion runtime:

- Resuelve `TranslationProvider` via `TranslationProviderFactory`.
- Construye `TranslatorService` y gateways concretos.
- Inyecta `providerId` al orquestador para evitar resolucion de providers dentro del flujo.

## Servicios de infraestructura

- `PdfExtractorService`: extrae texto embebido con PDFBox y detecta columnas.
- `PdfToParagraphService`: OCR con Tess4J, render a imagen y deteccion de columnas.
- `TranslatorService`: integra Ollama via HTTP, segmenta texto y aplica cache SQLite.
- `PdfRebuilderService`: escribe el PDF traducido preservando layout base y columnas.

## Dominio

- `TranslationUnit`: unidad canonica de trabajo de traduccion en runtime.
- `Paragraph`: artefacto de extraccion/rebuild (posicion y correlacion visual).
- `PageMeta`: metadatos por pagina (tamano, margenes, columnas, splitX).
- `TextBlock`: bloque de texto legacy para traduccion por bloques.

## Dependencias clave

- JavaFX (`javafx-controls`, `javafx-fxml`)
- PDFBox (`org.apache.pdfbox:pdfbox`)
- Tess4J (`net.sourceforge.tess4j:tess4j`)
- SQLite JDBC (`org.xerial:sqlite-jdbc`)
- JSON (`org.json:json`)
- Logging (`logback-classic`)
- Testing (`junit-jupiter`, `mockito`)

## Nota sobre componentes legacy

- Existe `com.dndtranslator.ui.App` como UI alternativa minima.
- `TranslationProgressListener` se mantiene como contrato legacy y esta marcado como `@Deprecated`.
- `TranslationCacheService` existe, pero el cache activo se gestiona dentro de `TranslatorService`.
