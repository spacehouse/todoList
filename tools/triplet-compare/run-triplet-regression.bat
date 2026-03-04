@echo off
setlocal enabledelayedexpansion

REM One-click regression entry for COMMAND_RESULT_TRIPLET compare (Windows)
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%..\..") do set "PROJECT_ROOT=%%~fI"
set "COMPARE_SCRIPT=%SCRIPT_DIR%compare-command-result-triplet.ps1"

if "%~1"=="" (
    echo [triplet-regression] Usage:
    echo   %~nx0 ^<logPath^> [baselinePath] [reportPath] [ignoreOrder]
    echo.
    echo Example:
    echo   %~nx0 ".\run.log"
    echo   %~nx0 ".\run.log" ".\tools\triplet-compare\baseline-sample.json" ".\build\reports\triplet-diff.json" true
    exit /b 2
)

set "LOG_PATH=%~1"
if "%~2"=="" (
    set "BASELINE_PATH=%SCRIPT_DIR%baseline-sample.json"
) else (
    set "BASELINE_PATH=%~2"
)

if "%~3"=="" (
    set "REPORT_PATH=%PROJECT_ROOT%\build\reports\triplet-diff.json"
) else (
    set "REPORT_PATH=%~3"
)

set "IGNORE_ORDER=false"
if /I "%~4"=="true" set "IGNORE_ORDER=true"
if /I "%~4"=="1" set "IGNORE_ORDER=true"
if /I "%~4"=="yes" set "IGNORE_ORDER=true"

if not exist "%COMPARE_SCRIPT%" (
    echo [triplet-regression] ERROR: compare script not found "%COMPARE_SCRIPT%"
    exit /b 2
)

set "PS_CMD=& '%COMPARE_SCRIPT%' -LogPath '%LOG_PATH%' -BaselinePath '%BASELINE_PATH%' -ReportPath '%REPORT_PATH%'"
if /I "%IGNORE_ORDER%"=="true" (
    set "PS_CMD=%PS_CMD% -IgnoreOrder"
)

echo [triplet-regression] logPath=%LOG_PATH%
echo [triplet-regression] baselinePath=%BASELINE_PATH%
echo [triplet-regression] reportPath=%REPORT_PATH%
echo [triplet-regression] ignoreOrder=%IGNORE_ORDER%
powershell -NoProfile -ExecutionPolicy Bypass -Command "%PS_CMD%"
set "EXIT_CODE=%ERRORLEVEL%"
exit /b %EXIT_CODE%
