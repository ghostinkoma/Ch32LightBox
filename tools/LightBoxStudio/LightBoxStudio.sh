#!/usr/bin/env bash
# LightBox Studio 起動ランチャ (Linux/macOS)
#   Java 検出順: ①同梱ポータブルJRE(dist/runtime/) → ②JAVA_HOME → ③PATH
set -eu
cd "$(dirname "$0")"

JAVA=""
for d in dist/runtime/*/bin/java; do [ -x "$d" ] && JAVA="$d" && break; done
if [ -z "$JAVA" ] && [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then JAVA="$JAVA_HOME/bin/java"; fi
if [ -z "$JAVA" ] && command -v java >/dev/null 2>&1; then JAVA="java"; fi

if [ -z "$JAVA" ]; then
  echo "[!] Java が見つかりません。PATH を通すか JAVA_HOME を設定してください。"
  exit 1
fi

echo "使用するJava: $JAVA"
exec "$JAVA" -jar dist/LightBoxMenu.jar
