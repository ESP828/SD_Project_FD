@echo off
chcp 65001 >nul
setlocal EnableExtensions
title FOODUCK Server

set "ROOT_DIR=%~dp0"
set "APP_DIR=%ROOT_DIR%springboot"
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
echo ========================================================
echo FOODUCK 서버를 단일 콘솔에서 시작합니다.
echo FastAPI : http://127.0.0.1:8000
echo Spring  : http://localhost:8081
echo 종료    : Ctrl+C
echo ========================================================
echo.

:: /B keeps the current console, and /WAIT keeps this launcher attached to the supervisor.
start "" /B /WAIT powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%RUN_SCRIPT%"
set "EXIT_CODE=%ERRORLEVEL%"

:: Windows uses 0xC000013A when a console process is interrupted with Ctrl+C.
if "%EXIT_CODE%"=="-1073741510" set "EXIT_CODE=0"
if "%EXIT_CODE%"=="3221225786" set "EXIT_CODE=0"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] 서버 실행에 실패했습니다. 위 메시지를 확인해주세요.
    pause
)

endlocal & exit /b %EXIT_CODE%
