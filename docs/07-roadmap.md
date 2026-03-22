# Roadmap sugerido
## Objetivo general
Mejorar calidad de traduccion y fidelidad de layout sin perder estabilidad operativa.
## Prioridad alta
1. Pruebas sobre PDF reales de doble columna
   - dataset de documentos reales (embebido + escaneado)
   - comparacion automatica de orden de lectura
2. Cobertura de pruebas para `PdfRebuilderService`
   - cortes de linea por columna
   - fuentes con y sin cobertura CJK
3. Robustez de `TranslatorService`
   - circuit breaker simple para caidas de Ollama
   - metrica de latencia por segmento y reintentos
## Prioridad media
1. Mejoras de OCR
   - perfiles por tipo de documento (contraste, ruido)
   - deteccion de tablas/sidebars para evitar orden incorrecto
2. Gestion avanzada de glosario
   - niveles de prioridad por termino
   - soporte de variaciones morfologicas
3. Observabilidad
   - logs estructurados por etapa
   - reporte final con estadisticas por corrida
## Prioridad baja
1. UI
   - selector de idioma destino en interfaz
   - panel de configuracion de hilos/modelo
2. Empaquetado
   - distribucion ejecutable para Windows
3. Limpieza tecnica
   - revisar y retirar componentes legacy no usados (`App`, `FontLoader`, `TranslationCacheService`) si ya no aportan
## Criterios de exito
- Menos fallbacks OCR innecesarios.
- Menos reemplazos por `?` en salida final.
- Mejor estabilidad en traducciones largas.
- Mayor confianza por pruebas repetibles.
