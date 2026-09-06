@echo off
setlocal EnableExtensions EnableDelayedExpansion

if "%~1"=="" goto :usage

set "TARGET_MC=%~1"
shift
set "GRADLE_ARGS="
:collect_args
if "%~1"=="" goto :args_done
set GRADLE_ARGS=!GRADLE_ARGS! "%~1"
shift
goto :collect_args
:args_done

set "ROOT_DIR=%~dp0"
set "MATRIX_FILE=%ROOT_DIR%gradle\version-matrix.properties"
set "MODTEST_VERSIONS_DIR=E:\MC\modtest\versions"

if not exist "%MATRIX_FILE%" (
    echo [1.21.x] Missing version matrix file: %MATRIX_FILE%
    exit /b 1
)

call :load_profile "%TARGET_MC%"
if errorlevel 1 exit /b %errorlevel%

if /i not "!PROFILE_BUILD_SUPPORTED!"=="true" (
    echo [1.21.x] Local build is not enabled for %TARGET_MC%.
    if defined PROFILE_STATUS echo [1.21.x] Status: !PROFILE_STATUS!
    exit /b 1
)

set "JAVA_HOME="
if exist "D:\JAVA\JDK\jdk-21.0.2\bin\javac.exe" (
    set "JAVA_HOME=D:\JAVA\JDK\jdk-21.0.2"
) else (
    if exist "D:\JAVA\JDK\jdk-21\bin\javac.exe" (
        set "JAVA_HOME=D:\JAVA\JDK\jdk-21"
    ) else (
        echo [1.21.x] Unable to locate a Java 21 installation under D:\JAVA\JDK
        exit /b 1
    )
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%ROOT_DIR%"

echo [1.21.x] Target Minecraft: %TARGET_MC%
echo [1.21.x] API group: !PROFILE_API_GROUP!
echo [1.21.x] Fabric Loader: !PROFILE_LOADER_VERSION!
echo [1.21.x] Fabric API: !PROFILE_FABRIC_API_VERSION!
echo [1.21.x] NeoForge: !PROFILE_NEOFORGE_VERSION!
echo [1.21.x] ModMenu: !PROFILE_MODMENU_VERSION!
if /i not "!PROFILE_FORGE_SUPPORTED!"=="false" (
    echo [1.21.x] Forge: !PROFILE_FORGE_VERSION! ^(build enabled^)
) else (
    echo [1.21.x] Forge build: disabled ^(Fabric + NeoForge only^)
)

call gradlew.bat ^
  "-Ptarget_matrix_profile=%TARGET_MC%" ^
  "-Ptarget_minecraft_version=!PROFILE_MINECRAFT_VERSION!" ^
  "-Ptarget_loader_version=!PROFILE_LOADER_VERSION!" ^
  "-Ptarget_fabric_api_version=!PROFILE_FABRIC_API_VERSION!" ^
  "-Ptarget_neoforge_version=!PROFILE_NEOFORGE_VERSION!" ^
  "-Ptarget_forge_version=!PROFILE_FORGE_VERSION!" ^
  "-Ptarget_modmenu_version=!PROFILE_MODMENU_VERSION!" ^
  "-Ptarget_fabric_loader_dependency=!PROFILE_FABRIC_LOADER_DEPENDENCY!" ^
  "-Ptarget_fabric_minecraft_dependency=!PROFILE_FABRIC_MINECRAFT_DEPENDENCY!" ^
  "-Ptarget_minecraft_version_range=!PROFILE_MINECRAFT_VERSION_RANGE!" ^
  "-Ptarget_forge_loader_range=!PROFILE_FORGE_LOADER_RANGE!" ^
  "-Ptarget_neoforge_loader_range=!PROFILE_NEOFORGE_LOADER_RANGE!" ^
  "-Ptarget_forge_supported=!PROFILE_FORGE_SUPPORTED!" ^
  "-Ptarget_api_group=!PROFILE_API_GROUP!" ^
  !GRADLE_ARGS!
if errorlevel 1 exit /b %errorlevel%

call :copy_artifacts
exit /b %errorlevel%

:usage
echo Usage: build-local-121x.bat ^<target-minecraft-version^> [Gradle args...]
echo Example: build-local-121x.bat 1.21.1 build --offline
echo Example: build-local-121x.bat 1.21.1 :common:check --offline
exit /b 1

:load_profile
set "PROFILE_FOUND="
for %%K in (
    PROFILE_BUILD_SUPPORTED
    PROFILE_API_GROUP
    PROFILE_MINECRAFT_VERSION
    PROFILE_LOADER_VERSION
    PROFILE_FABRIC_API_VERSION
    PROFILE_NEOFORGE_VERSION
    PROFILE_FORGE_VERSION
    PROFILE_MODMENU_VERSION
    PROFILE_FABRIC_LOADER_DEPENDENCY
    PROFILE_FABRIC_MINECRAFT_DEPENDENCY
    PROFILE_MINECRAFT_VERSION_RANGE
    PROFILE_FORGE_LOADER_RANGE
    PROFILE_NEOFORGE_LOADER_RANGE
    PROFILE_FORGE_SUPPORTED
    PROFILE_STATUS
) do set "%%K="

for /f "usebackq tokens=1* delims==" %%A in (`findstr /b /i /c:"%~1." "%MATRIX_FILE%"`) do (
    set "ENTRY_KEY=%%A"
    set "ENTRY_VALUE=%%B"
    set "ENTRY_KEY=!ENTRY_KEY:%~1.=!"
    set "PROFILE_FOUND=1"
    if /i "!ENTRY_KEY!"=="build_supported" set "PROFILE_BUILD_SUPPORTED=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="api_group" set "PROFILE_API_GROUP=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="minecraft_version" set "PROFILE_MINECRAFT_VERSION=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="loader_version" set "PROFILE_LOADER_VERSION=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="fabric_api_version" set "PROFILE_FABRIC_API_VERSION=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="neoforge_version" set "PROFILE_NEOFORGE_VERSION=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="forge_version" set "PROFILE_FORGE_VERSION=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="modmenu_version" set "PROFILE_MODMENU_VERSION=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="fabric_loader_dependency" set "PROFILE_FABRIC_LOADER_DEPENDENCY=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="fabric_minecraft_dependency" set "PROFILE_FABRIC_MINECRAFT_DEPENDENCY=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="minecraft_version_range" set "PROFILE_MINECRAFT_VERSION_RANGE=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="forge_loader_range" set "PROFILE_FORGE_LOADER_RANGE=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="neoforge_loader_range" set "PROFILE_NEOFORGE_LOADER_RANGE=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="forge_supported" set "PROFILE_FORGE_SUPPORTED=!ENTRY_VALUE!"
    if /i "!ENTRY_KEY!"=="status" set "PROFILE_STATUS=!ENTRY_VALUE!"
)

if not defined PROFILE_FOUND (
    echo [1.21.x] Version %~1 is not defined in the matrix.
    exit /b 1
)

if not defined PROFILE_MINECRAFT_VERSION (
    echo [1.21.x] Missing minecraft_version for %~1 in the matrix.
    exit /b 1
)

if not defined PROFILE_API_GROUP (
    echo [1.21.x] Missing api_group for %~1 in the matrix.
    exit /b 1
)
exit /b 0

:copy_artifacts
set "MOD_VERSION="
for /f "tokens=2 delims== " %%a in ('findstr /b "mod_version" "%ROOT_DIR%gradle.properties"') do set "MOD_VERSION=%%a"
if not defined MOD_VERSION exit /b 0

if not exist "%ROOT_DIR%dist" mkdir "%ROOT_DIR%dist"

call :copy_platform_jars "fabric"
call :copy_platform_jars "neoforge"
if /i not "!PROFILE_FORGE_SUPPORTED!"=="false" call :copy_platform_jars "forge"
exit /b 0

:copy_platform_jars
set "PLATFORM=%~1"
set "JAR_NAME=todolist-%PLATFORM%-%TARGET_MC%-%MOD_VERSION%.jar"
set "JAR_PATH=%ROOT_DIR%%PLATFORM%\build\libs\%JAR_NAME%"
if not exist "%JAR_PATH%" (
    echo [1.21.x] Artifact not found, skip copy: %PLATFORM%\build\libs\%JAR_NAME%
    exit /b 0
)
copy /Y "%JAR_PATH%" "%ROOT_DIR%dist\" >nul
echo [1.21.x] Copied !JAR_NAME! to dist\
call :copy_to_modtest "%ROOT_DIR%dist\!JAR_NAME!" "%PLATFORM%"
exit /b 0

:copy_to_modtest
set "SRC_JAR=%~1"
set "PLATFORM=%~2"
set "PLATFORM_NAME=%PLATFORM%"
if /i "%PLATFORM%"=="fabric" set "PLATFORM_NAME=Fabric"
if /i "%PLATFORM%"=="forge" set "PLATFORM_NAME=Forge"
if /i "%PLATFORM%"=="neoforge" set "PLATFORM_NAME=NeoForge"
if not exist "%MODTEST_VERSIONS_DIR%" exit /b 0
for /d %%D in ("%MODTEST_VERSIONS_DIR%\%TARGET_MC%-%PLATFORM_NAME%*") do (
    if exist "%%~fD" (
        if not exist "%%~fD\mods" mkdir "%%~fD\mods" >nul 2>&1
        copy /y "%SRC_JAR%" "%%~fD\mods\" >nul
        if errorlevel 1 (
            echo [1.21.x] Copy failed: "%~nx1" ^> "%%~fD\mods"
        ) else (
            echo [1.21.x] Copied "%~nx1" ^> "%%~fD\mods"
        )
    )
)
exit /b 0
