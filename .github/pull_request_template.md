## Resumen
<!-- ¿Qué cambia y por qué? -->

## Tipo de cambio
- [ ] Feature
- [ ] Fix
- [ ] Refactor / rendimiento
- [ ] IA on-device (prompts, modelo, motor)
- [ ] UI / diseño Kinetic Dark
- [ ] CI / build

## Cómo se probó
- [ ] `./gradlew :core:test testDebugUnitTest`
- [ ] `./gradlew lintDebug`
- [ ] Probado en Galaxy S25 Ultra (modelo: ___ · backend: GPU/CPU)

## Impacto en rendimiento
<!-- Latencia primer token, tokens/s, RAM pico, jank a 120 Hz. "N/A" si no aplica. -->

## Base de datos
- [ ] Sin cambios de esquema
- [ ] Cambio de esquema con migración Room y `schemas/` actualizado

## Checklist
- [ ] No se versionan modelos (`*.litertlm`, `*.task`, `*.bin`)
- [ ] Colores vía `KineticColors` / `MaterialTheme`, sin hex sueltos
- [ ] Textos visibles en `strings.xml`

## Capturas / vídeo
