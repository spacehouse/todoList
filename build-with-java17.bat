@echo off
setlocal
set JAVA_HOME=D:\JAVA\JDK\jdk-17.0.4
set PATH=D:\JAVA\JDK\jdk-17.0.4\bin;%PATH%
cd /d "E:\AI\claude\TodoList"
"E:\AI\claude\TodoList\gradlew.bat" build --no-daemon
endlocal
