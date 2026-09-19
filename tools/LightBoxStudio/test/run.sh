#!/usr/bin/env bash
# ConfigSchema.validate() 自己テスト (JUnit 非依存)
#   前提: 先に ../build.sh を実行して build/classes がある状態。
#   使い方: test/run.sh   (tools/LightBoxStudio から、または任意のcwdから)
set -euo pipefail
cd "$(dirname "$0")/.."   # tools/LightBoxStudio

[ -d build/classes ] || { echo "build/classes が無い。先に ./build.sh を実行してください"; exit 2; }

mkdir -p test-classes
javac --release 11 -encoding UTF-8 -cp build/classes -d test-classes test/ConfigSchemaCheck.java
java -cp build/classes:test-classes com.lightbox.studio.config.ConfigSchemaCheck ../../source/config.h
