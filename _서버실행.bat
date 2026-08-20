@echo off
chcp 65001 >nul
setlocal EnableExtensions
title FOODUCK Server

set "ROOT_DIR=%~dp0"
set "APP_DIR=%ROOT_DIR%springboot"
set "AI_DIR=%APP_DIR%\ai"
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
echo FOODUCK 로컬 실행 설정을 불러옵니다.
echo ========================================================
echo.

:: 💡 [1단계] AI 추천 모델 자동 실행 및 최신화 (괄호 문법 오류 해결)
if exist "%AI_DIR%\build_embeddings.py" (
    echo [1/3] AI 딥러닝 임베딩 생성을 진행합니다...
    pushd "%AI_DIR%"
    python build_embeddings.py
    if errorlevel 1 (
        echo [WARN] 임베딩 생성 중 오류가 발생했거나 Python이 없습니다.
    ) else (
        echo [INFO] 식당 딥러닝 임베딩 최신화 완료!
    )
    popd
    echo.
) else if exist "%AI_DIR%\train.py" (
    echo [1/2] AI 모델 사전 학습을 진행합니다...
    pushd "%AI_DIR%"
    python train.py
    if errorlevel 1 (
        echo [WARN] AI 모델 학습 중 오류가 발생했거나 Python이 없습니다.
    ) else (
        echo [INFO] AI 모델 사전 최신화 완료!
    )
    popd
    echo.
) else (
    echo [WARN] AI 학습 스크립트를 찾을 수 없어 건너뜁니다.
    echo.
)

:: 💡 [2단계] AI 임베딩 검색 서비스(FastAPI) 상시 기동 - 별도 창 없이 현재 콘솔 로그에 편입
if exist "%AI_DIR%\app.py" (
    echo [2/3] AI 의미 검색 서비스^(FastAPI^)를 이 창에서 백그라운드로 시작합니다...
    pushd "%AI_DIR%"
    start "" /B python -m uvicorn app:app --host 127.0.0.1 --port 8000
    popd
    echo [INFO] AI 서비스 로그는 아래에 이어서 출력됩니다.
    echo         ^(Python/uvicorn이 없으면 Spring Boot는 자동으로 TF-IDF 폴백으로 동작합니다^)
    echo.
) else (
    echo [WARN] AI 서비스 스크립트^(app.py^)를 찾을 수 없어 건너뜁니다.
    echo.
)

:: 💡 [3단계] Spring Boot 서버 실행
echo [3/3] Spring Boot 백엔드 서버를 시작합니다...
echo 서버를 종료하려면 Ctrl+C를 누르세요.
echo ========================================================
echo.

powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%RUN_SCRIPT%"
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo [ERROR] The server failed to start. Check the message above.
    pause
)

endlocal & exit /b %EXIT_CODE%