@echo off
setlocal EnableExtensions

cd /d "%~dp0" || exit /b 1

rem Legacy filename: this entry builds files for manual import; it does not install.
set "BUILD_MODE="
if not "%~2"=="" goto usage
if not "%~1"=="" (
    if /I not "%~1"=="--offline" goto usage
    set "BUILD_MODE=--offline"
)

python --version >nul 2>&1
if errorlevel 1 (
    echo ERROR: Python is required to validate and stage the Fabric release.
    pause
    exit /b 1
)

echo Builds a Fabric release for manual import. No Minecraft instance is changed.
echo [1/2] Building with the repository Gradle Wrapper...
call "%~dp0gradlew.bat" build %BUILD_MODE%
if errorlevel 1 (
    echo ERROR: Build failed. No release was staged.
    pause
    exit /b 1
)

echo [2/2] Validating and staging the Fabric release...
python "%~dp0tools\fabric\prepare-ci-release.py"
if errorlevel 1 (
    echo ERROR: Release validation failed. Do not import a previous staged package.
    pause
    exit /b 1
)

echo READY FOR MANUAL IMPORT: "%CD%\build\ci-release"
echo This folder contains the release JAR, SHA256 and release.json.
echo After backing up, import the JAR into a confirmed Minecraft 26.3 Fabric test instance.
echo Existing Minecraft packages, configuration and worlds were not changed.
pause
exit /b 0

:usage
echo Usage: "%~nx0" [--offline]
echo Builds and validates files for manual import in build\ci-release; does not install.
exit /b 1
