@echo off
REM LightBox Studio ビルド (Windows)
REM   依存: JDK 11+ (javac / jar) に PATH が通っていること
REM   生成物: dist\ 以下に 4本の JAR
setlocal
cd /d "%~dp0"

set SRC=src
set OUT=build\classes
set DIST=dist
set REL=11

echo == compile ==
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%"
if not exist build mkdir build
dir /s /b "%SRC%\*.java" > build\sources.txt
javac --release %REL% -encoding UTF-8 -d "%OUT%" @build\sources.txt
if errorlevel 1 goto :err

echo == jar ==
if not exist "%DIST%" mkdir "%DIST%"
jar --create --file "%DIST%\LightBoxMenu.jar"   --main-class com.lightbox.studio.menu.MenuApp        -C "%OUT%" .
jar --create --file "%DIST%\config-editor.jar"  --main-class com.lightbox.studio.config.ConfigEditor -C "%OUT%" .
jar --create --file "%DIST%\builder.jar"        --main-class com.lightbox.studio.build.BuilderMain   -C "%OUT%" .
jar --create --file "%DIST%\flasher.jar"        --main-class com.lightbox.studio.flash.FlasherMain    -C "%OUT%" .
jar --create --file "%DIST%\setup.jar"          --main-class com.lightbox.studio.setup.SetupWizard    -C "%OUT%" .

if exist settings.properties.sample copy /y settings.properties.sample "%DIST%\" >nul
if exist toolchain.manifest.properties.sample copy /y toolchain.manifest.properties.sample "%DIST%\" >nul

echo done -^> %DIST%\
dir /b "%DIST%"
goto :eof

:err
echo ビルドに失敗しました。
exit /b 1
