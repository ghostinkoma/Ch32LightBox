@echo off
REM LightBox Studio 起動ランチャ (Windows)
REM   Java の検出順: ①同梱ポータブルJRE(dist\runtime\) → ②JAVA_HOME → ③PATH
REM   ※システムへのインストールや PATH/レジストリ変更は一切しない。
setlocal enabledelayedexpansion
cd /d "%~dp0"

set "JW="
REM (1) ポータブルJRE（サンドボックス: このフォルダ内のみ）
for /d %%D in ("dist\runtime\*") do if exist "%%D\bin\javaw.exe" set "JW=%%D\bin\javaw.exe"
REM (2) JAVA_HOME
if not defined JW if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javaw.exe" set "JW=%JAVA_HOME%\bin\javaw.exe"
REM (3) PATH（javaw → java の順）
if not defined JW for %%J in (javaw.exe) do if not "%%~$PATH:J"=="" set "JW=%%~$PATH:J"
if not defined JW for %%J in (java.exe)  do if not "%%~$PATH:J"=="" set "JW=%%~$PATH:J"

if not defined JW (
  echo.
  echo [!] Java が見つかりません。
  echo     - 既存のJavaを使う: PATH を通すか JAVA_HOME を設定してください。
  echo     - システムに入れたくない場合: メニューの［初回セットアップ］で
  echo       「ポータブルJRE も取得」を選ぶと dist\runtime\ にのみ展開されます
  echo       （システムには影響しません）。※この取得には一時的にJavaが必要です。
  echo.
  pause
  exit /b 1
)

echo 使用するJava: "%JW%"
start "" "%JW%" -jar "dist\LightBoxMenu.jar"
