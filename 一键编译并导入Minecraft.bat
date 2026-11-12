@echo off
setlocal EnableExtensions EnableDelayedExpansion

cd /d "%~dp0"

rem Use the verified local Gradle distribution. Network access is disabled.
set "GRADLE_BIN=C:\Users\Administrator\.gradle\wrapper\dists\gradle-9.7.0-bin\d4tj7w02tcgubx9zk9hbippn6\gradle-9.7.0\bin\gradle.bat"
set "TARGET_MODS=D:\Minecraft\.minecraft\versions\xianfanjie\mods"
set "TARGET_JAR=%TARGET_MODS%\myriad-0.1.0.jar"

if not exist "%GRADLE_BIN%" (
    echo ERROR: Local Gradle 9.7.0 was not found.
    echo Expected: %GRADLE_BIN%
    pause
    exit /b 1
)

if not exist "%TARGET_MODS%\" (
    echo ERROR: Minecraft mods folder was not found.
    echo Expected: %TARGET_MODS%
    pause
    exit /b 1
)

echo [1/2] Building mod offline...
call "%GRADLE_BIN%" build --offline
if errorlevel 1 (
    echo ERROR: Build failed. No file was copied.
    pause
    exit /b 1
)

set "MOD_JAR="
for /f "delims=" %%F in ('dir /b /a-d /o-d "build\libs\myriad-*.jar" ^| findstr /v /i /c:"-sources.jar" /c:"-dev.jar"') do (
    if not defined MOD_JAR set "MOD_JAR=%%F"
)

if not defined MOD_JAR (
    echo ERROR: No mod JAR was found in build\libs.
    pause
    exit /b 1
)

echo [2/2] Copying !MOD_JAR!...
copy /Y "build\libs\!MOD_JAR!" "%TARGET_JAR%" >nul
if errorlevel 1 (
    echo ERROR: Copy failed. Close Minecraft and try again.
    pause
    exit /b 1
)

echo DONE: %TARGET_JAR%
pause
