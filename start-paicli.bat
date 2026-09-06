@echo off
setlocal

cd /d "%~dp0"
chcp 936 >nul

set "JAR_PATH=target\paicli-1.0-SNAPSHOT.jar"

if /I "%~1"=="build" (
    echo [Simple CLI] Building jar...
    call mvn -q -DskipTests clean package
    if errorlevel 1 (
        echo [Simple CLI] Build failed.
        exit /b 1
    )
)

if not exist "%JAR_PATH%" (
    echo [Simple CLI] Jar not found, building first...
    call mvn -q -DskipTests clean package
    if errorlevel 1 (
        echo [Simple CLI] Build failed.
        exit /b 1
    )
)

echo [Simple CLI] Starting with GBK terminal encoding...
java "-Dorg.jline.terminal.stdin.encoding=GBK" "-Dorg.jline.terminal.stdout.encoding=GBK" "-Dorg.jline.terminal.stderr.encoding=GBK" -jar "%JAR_PATH%"

endlocal
