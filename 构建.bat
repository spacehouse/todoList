@echo off
REM 构建Todo List Mod
REM 需要网络可以访问 https://maven.fabricmc.net/

echo ========================================
echo 开始构建 Todo List Mod
echo ========================================

cd /d E:\AI\MC\todoList

REM 设置Java 17
set JAVA_HOME=D:\program\JAVA\JDK\jdk17

REM 使用Gradle 8.1.1构建
echo.
echo 步骤1: 清理旧构建...
call .\gradlew.bat clean --no-daemon

if errorlevel 1 (
    echo 清理失败！
    pause
    exit /b 1
)

echo.
echo 步骤2: 构建项目...
call .\gradlew.bat build --no-daemon

if errorlevel 1 (
    echo 构建失败！
    pause
    exit /b 1
)

echo.
echo ========================================
echo 构建成功！
echo jar文件位置: build\libs\todolist-fabric-1.0.0.jar
echo ========================================

pause
