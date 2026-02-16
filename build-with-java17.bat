@echo off
setlocal
set JAVA_HOME=D:\program\JAVA\JDK\jdk17
set PATH=D:\program\JAVA\JDK\jdk17\bin;%PATH%
cd /d "E:\AI\MC\todoList"
"E:\AI\MC\todoList\gradlew.bat" %*
endlocal
