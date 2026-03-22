# Documentacion del proyecto DnD Translator

Este directorio centraliza la documentacion funcional y tecnica del proyecto.

## Orden recomendado de lectura

1. `docs/01-instalacion-y-ejecucion.md`
2. `docs/02-arquitectura.md`
3. `docs/03-flujo-de-traduccion.md`
4. `docs/04-modulos-y-servicios.md`
5. `docs/05-pruebas.md`
6. `docs/06-troubleshooting.md`
7. `docs/07-roadmap.md`
8. `docs/plan-accion-traductor.md` (historial de cambios ya ejecutados)

## Cobertura de esta documentacion

- Como preparar el entorno (Java, Maven, Ollama, OCR, fuentes).
- Como ejecutar la app y que artefactos genera.
- Como esta organizado el codigo en capas y paquetes.
- Flujo completo de extraccion -> traduccion -> reconstruccion PDF.
- Responsabilidades de cada servicio principal y modelos de datos.
- Estrategia de pruebas automatizadas y comandos utiles.
- Problemas frecuentes en Windows y pasos de diagnostico.
- Siguientes mejoras propuestas para evolucionar el sistema.

## Alcance

La documentacion describe el estado actual observado en `src/main/java` y `src/test/java`.
Si se modifica el flujo principal, se recomienda actualizar primero:

- `docs/03-flujo-de-traduccion.md`
- `docs/04-modulos-y-servicios.md`
- `docs/05-pruebas.md`

