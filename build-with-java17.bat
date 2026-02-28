@echo off
set JAVA_HOME=D:\JAVA\JDK\jdk-17.0.4
set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "%~dp0"
call gradlew.bat %*