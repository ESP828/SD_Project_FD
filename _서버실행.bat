@echo off
chcp 65001 >nul
setlocal EnableExtensions
title FOODUCK Server

set "APP_DIR=%~dp0springboot"
set "RUN_SCRIPT=%APP_DIR%\run-local.ps1"
set "ENV_FILE=%APP_DIR%\.env"

if not exist "%APP_DIR%\mvnw.cmd" (
    echo [ERROR] Maven Wrapper를 찾을 수 없습니다.
    echo         %APP_DIR%\mvnw.cmd
    pause
    exit /b 1
)

if not exist "%RUN_SCRIPT%" (
    echo [ERROR] 로컬 실행 스크립트를 찾을 수 없습니다.
    echo         %RUN_SCRIPT%
    pause
    exit /b 1
)

if not exist "%ENV_FILE%" (
    echo [ERROR] 환경변수 파일을 찾을 수 없습니다.
    echo         %ENV_FILE%
    pause
    exit /b 1
)

if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" goto java_ready
    echo [WARN] JAVA_HOME 경로가 올바르지 않아 PATH의 Java를 사용합니다.
    set "JAVA_HOME="
)

where.exe java.exe >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java를 찾을 수 없습니다. JDK 17 이상을 설치하고
    echo         JAVA_HOME 또는 PATH를 설정해주세요.
    pause
    exit /b 1
)

:java_ready
echo FOODUCK 로컬 실행 설정을 불러옵니다.
echo 서버를 종료하려면 Ctrl+C를 누르세요.
echo.

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%RUN_SCRIPT%"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] The server failed to start. Check the message above.
    pause
)

endlocal & exit /b %EXIT_CODE%
