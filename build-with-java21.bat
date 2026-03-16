@echo off
if "%JAVA_HOME%"=="" (
    set "JAVA_HOME=D:\JAVA\JDK\jdk-21.0.2"
)
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%~dp0"
call gradlew.bat %*
