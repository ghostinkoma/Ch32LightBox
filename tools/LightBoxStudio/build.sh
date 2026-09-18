#!/usr/bin/env bash
# LightBox Studio ビルド (Linux/macOS)
#   依存: JDK 11+ (javac / jar)
#   生成物: dist/ 以下に 4本の JAR + settings.properties.sample
set -euo pipefail
cd "$(dirname "$0")"

SRC=src
OUT=build/classes
DIST=dist
REL=11   # 想定する最小Javaバージョン

echo "== compile =="
rm -rf "$OUT"; mkdir -p "$OUT"
find "$SRC" -name '*.java' > build/sources.txt
javac --release "$REL" -encoding UTF-8 -d "$OUT" @build/sources.txt

echo "== jar =="
mkdir -p "$DIST"
jar --create --file "$DIST/LightBoxMenu.jar"   --main-class com.lightbox.studio.menu.MenuApp     -C "$OUT" .
jar --create --file "$DIST/config-editor.jar"  --main-class com.lightbox.studio.config.ConfigEditor -C "$OUT" .
jar --create --file "$DIST/builder.jar"        --main-class com.lightbox.studio.build.BuilderMain  -C "$OUT" .
jar --create --file "$DIST/flasher.jar"        --main-class com.lightbox.studio.flash.FlasherMain  -C "$OUT" .
jar --create --file "$DIST/setup.jar"          --main-class com.lightbox.studio.setup.SetupWizard  -C "$OUT" .

cp -f settings.properties.sample "$DIST/" 2>/dev/null || true
cp -f toolchain.manifest.properties.sample "$DIST/" 2>/dev/null || true

echo "done -> $DIST/"
ls -1 "$DIST"
