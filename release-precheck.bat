@echo off
setlocal enabledelayedexpansion

REM Unified pre-release check entry (Windows):
REM 1) build
REM 2) COMMAND_RESULT_TRIPLET regression

set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%") do set "PROJECT_ROOT=%%~fI"
set "BUILD_SCRIPT=%PROJECT_ROOT%build-with-java17.bat"
set "TRIPLET_ENTRY=%PROJECT_ROOT%tools\triplet-compare\run-triplet-regression.bat"

if "%~1"=="" (
    echo [release-precheck] Usage:
    echo   %~nx0 ^<logPath^> [baselinePath] [reportPath] [ignoreOrder] [gradleTask]
    echo.
    echo Example:
    echo   %~nx0 ".\tools\triplet-compare\log-sample.txt"
    echo   %~nx0 ".\run.log" ".\tools\triplet-compare\baseline-sample.json" ".\build\reports\triplet-diff.json" true build
    exit /b 2
)

set "LOG_PATH=%~1"
set "BASELINE_PATH=%~2"
set "REPORT_PATH=%~3"
set "IGNORE_ORDER=%~4"
set "GRADLE_TASK=%~5"

if "%BASELINE_PATH%"=="" set "BASELINE_PATH=%PROJECT_ROOT%tools\triplet-compare\baseline-sample.json"
if "%REPORT_PATH%"=="" set "REPORT_PATH=%PROJECT_ROOT%build\reports\triplet-diff.json"
if "%IGNORE_ORDER%"=="" set "IGNORE_ORDER=false"
if "%GRADLE_TASK%"=="" set "GRADLE_TASK=build"

if not exist "%BUILD_SCRIPT%" (
    echo [release-precheck] ERROR: build script not found "%BUILD_SCRIPT%"
    exit /b 2
)

if not exist "%TRIPLET_ENTRY%" (
    echo [release-precheck] ERROR: triplet regression entry not found "%TRIPLET_ENTRY%"
    exit /b 2
)

echo [release-precheck] projectRoot=%PROJECT_ROOT%
echo [release-precheck] logPath=%LOG_PATH%
echo [release-precheck] baselinePath=%BASELINE_PATH%
echo [release-precheck] reportPath=%REPORT_PATH%
echo [release-precheck] ignoreOrder=%IGNORE_ORDER%
echo [release-precheck] gradleTask=%GRADLE_TASK%
echo [release-precheck] Step 1/2 build start...
call "%BUILD_SCRIPT%" %GRADLE_TASK%
set "BUILD_EXIT=%ERRORLEVEL%"
if not "%BUILD_EXIT%"=="0" (
    echo [release-precheck] FAIL: build step failed with exit code %BUILD_EXIT%
    exit /b %BUILD_EXIT%
)

echo [release-precheck] Step 1/2 build passed.
echo [release-precheck] Step 2/2 triplet regression start...
call "%TRIPLET_ENTRY%" "%LOG_PATH%" "%BASELINE_PATH%" "%REPORT_PATH%" "%IGNORE_ORDER%"
set "TRIPLET_EXIT=%ERRORLEVEL%"
if not "%TRIPLET_EXIT%"=="0" (
    echo [release-precheck] FAIL: triplet regression failed with exit code %TRIPLET_EXIT%
    exit /b %TRIPLET_EXIT%
)

echo [release-precheck] Step 2/2 triplet regression passed.
echo [release-precheck] PASS: all pre-release checks succeeded.
exit /b 0
