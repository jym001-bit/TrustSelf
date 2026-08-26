@echo off
rem MinBlog 一键启动（Windows）
chcp 65001 >nul
cd /d %~dp0

if not exist out mkdir out

echo [1/2] 编译中...
javac -encoding UTF-8 -d out src\com\minblog\*.java
if errorlevel 1 (
    echo.
    echo [失败] 编译出错，请确认已安装 JDK 8 或更高版本，且 javac 在 PATH 中。
    pause
    exit /b 1
)

echo [2/2] 启动服务...
echo 浏览器打开 http://localhost:8080 （按 Ctrl+C 停止）
echo.
java -Dfile.encoding=UTF-8 -cp out com.minblog.Main
pause
