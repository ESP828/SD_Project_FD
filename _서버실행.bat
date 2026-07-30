@echo off
chcp 65001 >nul
setlocal

cd /d "%~dp0springboot"
if errorlevel 1 (
    echo [ERROR] springboot folder not found.
    pause
    exit /b 1
)

set "SERVER_PORT=8081"
set "DB_URL=jdbc:mysql://0.tcp.jp.ngrok.io:20748/fooduck?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true"
set "DB_USERNAME=root"
set "DB_PASSWORD=molla0904)()$"
set "JWT_SECRET=fooduck-local-development-jwt-secret-at-least-32-characters"

echo Starting FOODUCK server at http://localhost:%SERVER_PORT%/
echo Press Ctrl+C to stop the server.
echo.

call mvnw.cmd spring-boot:run

if errorlevel 1 (
    echo.
    echo [ERROR] The server failed to start. Check the message above.
    pause
)

endlocal
