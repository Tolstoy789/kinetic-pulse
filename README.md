# ⚡ Kinetic Pulse

[![Android CI](https://github.com/Tolstoy789/kinetic-pulse/actions/workflows/android-ci.yml/badge.svg)](https://github.com/Tolstoy789/kinetic-pulse/actions/workflows/android-ci.yml)
![Kotlin](https://img.shields.io/badge/Kotlin-2.3-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)
![API](https://img.shields.io/badge/target-API%2035%20(Android%2015)-3DDC84?logo=android&logoColor=white)
![Edge AI](https://img.shields.io/badge/Edge%20AI-LiteRT--LM%20·%20GPU-CCFF00)
![Device](https://img.shields.io/badge/optimizado-Galaxy%20S25%20Ultra-00F0FF)

App nativa Android de seguimiento y programación **híbrida**: gimnasio con sobrecarga progresiva y calistenia con tiempos isométricos. Toda la IA corre **en el dispositivo** (sin red, sin enviar datos) sobre la GPU Adreno 830 del Snapdragon 8 Elite.

| | |
|---|---|
| 🎯 Dispositivo objetivo | Samsung Galaxy S25 Ultra · Android 15 / One UI 7 · 12 GB RAM |
| 🧠 Motor IA | [LiteRT-LM](https://ai.google.dev/edge/litert-lm/android) (sucesor de MediaPipe LLM Inference), backend **GPU** con *fallback* a CPU |
| ⚡ Modelo rápido | **Gemma 3 1B IT** (INT4): NLP Quick Log y respuestas cortas |
| 🧩 Modelo complejo | **Gemma 3n E4B IT** (INT4): microciclos y análisis narrado |
| 🎨 UI | Jetpack Compose + Material 3 · tema **Kinetic Dark** AMOLED · 120 Hz |
| 💾 Datos | Room (rutinas, sesiones, sets, métricas de gym y calistenia) |
| 💉 DI | Hilt |

---

## Funciones

- **NLP Quick Log**: escribe `4x10 planche lean 5s pausa` y el modelo local lo convierte a JSON tipado (`RoutineSetDto`). Los sets aparecen **en streaming**, uno a uno, según el modelo los genera. Sin modelo cargado, un parser heurístico determinista hace el trabajo para que el registro nunca se bloquee.
- **LiveWorkoutCard**: registro del set en curso con steppers grandes de kilos y reps, slider de RPE y temporizador isométrico sincronizado con el vsync, con barra de progreso luminosa.
- **Análisis de fatiga RPE**: carga interna por sesión (sRPE) y ratio agudo:crónico (7 frente a 28 días), con recomendación de progresión o descarga.

## Arquitectura

```
:core  (Kotlin/JVM puro, testeable sin Android)
 ├─ model/      RoutineSetDto, ExerciseModality
 ├─ quicklog/   QuickLogPrompt · StreamingJsonExtractor · HeuristicQuickLogParser
 └─ fatigue/    RpeFatigueAnalyzer (sRPE, ACWR, e1RM por RIR)

:app
 ├─ ai/         GemmaLocalManager · LlmEngine · LiteRtLmEngine · GemmaModel
 ├─ data/       Room (entities, DAOs, KineticDatabase) · WorkoutRepository
 ├─ di/         Hilt modules
 └─ ui/         theme/ (Color, Theme, Type) · components/ · workout/ (LiveWorkoutCard, WorkoutScreen)
```

Toda la API de LiteRT-LM está aislada en `LiteRtLmEngine.kt`. Si cambia entre versiones, solo se toca ese archivo.

---

## Puesta en marcha

### Requisitos

- Android Studio (última estable) con JDK 17
- Android SDK 36 instalado (compileSdk 36, targetSdk 35)
- `adb` en el PATH y **Depuración USB** activada en el S25 Ultra
  *(Ajustes → Información del teléfono → Información de software → pulsa 7 veces «Número de compilación»; luego Ajustes → Opciones de desarrollador → Depuración USB)*

```bash
git clone https://github.com/Tolstoy789/kinetic-pulse.git
cd kinetic-pulse
./gradlew :core:test testDebugUnitTest   # tests
./gradlew installDebug                    # instala en el teléfono conectado
```

> Instala la app **antes** de copiar los modelos: así Android crea su carpeta de datos.

### 1. Descargar los modelos Gemma

Los modelos **no se versionan** (el `.gitignore` y la CI lo impiden). Necesitas el formato **`.litertlm`**.

1. Crea una cuenta en [Hugging Face](https://huggingface.co) y **acepta la licencia de Gemma** en cada página de modelo (son repos con acceso restringido).
2. Crea un token de lectura en *Settings → Access Tokens*.
3. Descarga:

```bash
pip install -U "huggingface_hub[cli]"
hf auth login        # pega tu token

mkdir -p ~/kinetic-models && cd ~/kinetic-models

# Modelo rápido: Gemma 3 1B IT (INT4)
hf download litert-community/Gemma3-1B-IT --include "*.litertlm" --local-dir gemma3-1b

# Modelo complejo: Gemma 3n E4B IT (INT4)
hf download google/gemma-3n-E4B-it-litert-lm --include "*.litertlm" --local-dir gemma3n-e4b

ls -lh gemma3-1b gemma3n-e4b   # comprueba los nombres exactos de los .litertlm
```

> **Kaggle** es una alternativa: [kaggle.com/models/google/gemma-3](https://www.kaggle.com/models/google/gemma-3) y [gemma-3n](https://www.kaggle.com/models/google/gemma-3n), variante *LiteRT-LM*. Si un repo ofrece varias variantes, elige la INT4 (`q4` / `int4`). Para Gemma 3 1B, la de mayor contexto (`ekv4096`) es la recomendada.

### 2. Copiar los modelos al S25 Ultra con `adb push`

La app lee los modelos de su almacenamiento externo específico, accesible por `adb` sin root:

```
/sdcard/Android/data/com.kineticpulse.app/files/models/
```

Usa el script, que copia a un fichero temporal, comprueba que el tamaño en el teléfono coincide con el local y solo entonces le pone el nombre que espera la app (`GemmaModel.fileName`). Así un cable que falla a mitad nunca deja un modelo truncado:

```bash
adb devices                                    # el S25 Ultra debe aparecer como "device"

# Sustituye <archivo> por el .litertlm descargado en el paso anterior
scripts/push-models.sh --fast gemma3-1b/<archivo>.litertlm \
                       --complex gemma3n-e4b/<archivo>.litertlm
```

El script reinicia la app al terminar. Si aun así copias a mano, la app rechaza ficheros claramente incompletos (menos de 300 MB para Gemma 3 1B o de 2 GB para Gemma 3n E4B) y lo indica bajo el título.

> Usa un cable USB 3 (USB-C a USB-C): el modelo de 4B pesa varios GB.
> Si desinstalas la app, Android borra esta carpeta y tendrás que repetir el `push`.

#### Actualizar la app sin perder datos ni modelos

Los APK de debug de la CI se firman con `app/debug.keystore` (versionado, con las credenciales públicas de debug `android`/`android`), así que cada versión nueva se instala encima de la anterior:

```bash
adb install -r kinetic-pulse-debug.apk
```

Si instalaste un APK anterior a este cambio, desinstálalo una única vez (`adb uninstall com.kineticpulse.app`) y vuelve a copiar los modelos.

### 3. Activar y verificar el delegado GPU

La app pide GPU por defecto (`Backend.GPU()`, OpenCL sobre Adreno 830). No hace falta ninguna opción oculta, pero conviene comprobar lo siguiente:

1. **Librerías nativas.** El manifest ya declara `libOpenCL.so` y `libvndksupport.so` como `<uses-native-library>`. Sin esto, Android 12+ bloquea el acceso a OpenCL y se usa CPU. Comprueba que existen en el teléfono:
   ```bash
   adb shell ls /vendor/lib64/libOpenCL.so
   ```
2. **Rendimiento.** En *Ajustes → Batería → Ahorro de energía* debe estar **desactivado**: limita la frecuencia de la GPU.
3. **Pantalla a 120 Hz.** En *Ajustes → Pantalla → Fluidez de movimiento* elige **Adaptable**. La app pide el modo de mayor refresco para su ventana.
4. **Verificar el backend.** La cabecera de la app muestra `IA local: Gemma 3 1B · GPU`. Si dice `CPU`, el delegado falló. Mira el motivo en logcat:
   ```bash
   adb logcat -s GemmaLocalManager litertlm
   ```
5. **Primera carga.** Los kernels OpenCL se compilan y se cachean en `cacheDir/llm`. La primera carga tarda más que las siguientes.

### Gestión de memoria

- Solo **un modelo residente** a la vez. Al pedir el otro, se libera el actual primero.
- Antes de cargar se comprueba la RAM libre (`GemmaModel.minFreeRamMb`). Si no basta, se muestra un error en vez de provocar un cierre forzado por memoria.
- Si la app pasa a segundo plano y el sistema pide memoria (`onTrimMemory`), el modelo se descarga.

### Expectativas de rendimiento

Valores orientativos en el S25 Ultra (backend GPU), a medir en tu unidad:

| Modelo | Primer token | Decode |
|---|---|---|
| Gemma 3 1B INT4 | ~100–300 ms | decenas de tokens/s |
| Gemma 3n E4B INT4 | ~0.5–1.5 s | ~10–20 tokens/s |

Bajar de 30 ms hasta el primer token no es realista con un LLM local: el *prefill* del prompt domina. Por eso el Quick Log usa un prompt corto y muestra cada set en cuanto se completa.

---

## CI/CD

`.github/workflows/android-ci.yml` se ejecuta en cada push o PR a `main`:

1. Valida el Gradle wrapper y comprueba que no se ha versionado ningún modelo.
2. `lintDebug`
3. `:core:test` + `testDebugUnitTest`
4. `assembleDebug`. El APK queda como *artifact* descargable durante 14 días.

Dependabot propone actualizaciones semanales de Gradle y mensuales de Actions.

## Contribuir

Ramas `feat/…`, `fix/…`; PR contra `main` siguiendo la plantilla. Si cambias el esquema de Room, sube `version`, añade la migración y commitea `app/schemas/`.
