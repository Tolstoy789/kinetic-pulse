#!/usr/bin/env bash
# Copia los modelos .litertlm al S25 Ultra de forma segura:
#  1. push a "<nombre>.part" (la app nunca ve un fichero a medias),
#  2. compara el tamaño en el teléfono con el local,
#  3. renombra al nombre definitivo y reinicia la app.
#
# Uso:
#   scripts/push-models.sh --fast  gemma3-1b/<archivo>.litertlm
#   scripts/push-models.sh --complex gemma3n-e4b/<archivo>.litertlm
#   scripts/push-models.sh --fast A.litertlm --complex B.litertlm
set -euo pipefail

PACKAGE=com.kineticpulse.app
MODELS=/sdcard/Android/data/$PACKAGE/files/models
# Deben coincidir con GemmaModel.fileName.
FAST_NAME=gemma3-1b-it-int4.litertlm
COMPLEX_NAME=gemma-3n-E4B-it-int4.litertlm

usage() { sed -n '2,11p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

local_size() { stat -c %s "$1" 2>/dev/null || stat -f %z "$1"; }

push_one() {
  local src=$1 name=$2
  [[ -f $src ]] || { echo "✗ No existe: $src" >&2; exit 1; }
  local size; size=$(local_size "$src")
  echo "→ $src ($((size / 1024 / 1024)) MB) → $MODELS/$name"
  adb push "$src" "$MODELS/$name.part"
  local remote; remote=$(adb shell stat -c %s "$MODELS/$name.part" | tr -d '\r')
  if [[ $remote != "$size" ]]; then
    echo "✗ Tamaño distinto en el teléfono ($remote ≠ $size). Revisa el cable y repite." >&2
    adb shell rm -f "$MODELS/$name.part"
    exit 1
  fi
  adb shell mv -f "$MODELS/$name.part" "$MODELS/$name"
  echo "✓ $name verificado"
}

[[ $# -gt 0 ]] || usage
command -v adb >/dev/null || { echo "✗ adb no está en el PATH" >&2; exit 1; }
[[ $(adb get-state 2>/dev/null) == device ]] || { echo "✗ No hay ningún teléfono conectado (adb devices)" >&2; exit 1; }
adb shell pm path "$PACKAGE" >/dev/null || { echo "✗ Instala primero la app ($PACKAGE)" >&2; exit 1; }
adb shell mkdir -p "$MODELS"

while [[ $# -gt 0 ]]; do
  case $1 in
    --fast) push_one "$2" "$FAST_NAME"; shift 2 ;;
    --complex) push_one "$2" "$COMPLEX_NAME"; shift 2 ;;
    *) usage ;;
  esac
done

adb shell ls -lh "$MODELS"
adb shell am force-stop "$PACKAGE"
adb shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null
echo "✓ App reiniciada: el estado del motor aparece bajo el título"
