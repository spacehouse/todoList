@echo off
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "PROJECT_ROOT=%%~fI"
set "ENTRY_SCRIPT=%SCRIPT_DIR%init-screenshot-archive.ps1"

if not exist "%ENTRY_SCRIPT%" (
    echo [screenshot-archive] ERROR: entry script not found "%ENTRY_SCRIPT%"
    exit /b 2
)

set "ARCHIVE_BRANCH=%~1"
set "ARCHIVE_DATE=%~2"

if "%~1"=="/?" (
    echo [screenshot-archive] Usage:
    echo   %~nx0 [branch] [yyyyMMdd]
    echo.
    echo Examples:
    echo   %~nx0
    echo   %~nx0 main
    echo   %~nx0 release-2026Q1 20260304
    exit /b 0
)

set "PS_CMD=& '%ENTRY_SCRIPT%' -ProjectRoot '%PROJECT_ROOT%'"
if not "%ARCHIVE_BRANCH%"=="" set "PS_CMD=%PS_CMD% -Branch '%ARCHIVE_BRANCH%'"
if not "%ARCHIVE_DATE%"=="" set "PS_CMD=%PS_CMD% -Date '%ARCHIVE_DATE%'"

powershell -NoProfile -ExecutionPolicy Bypass -Command "%PS_CMD%"
set "EXIT_CODE=%ERRORLEVEL%"
exit /b %EXIT_CODE%
