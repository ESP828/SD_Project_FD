@echo off
setlocal EnableExtensions DisableDelayedExpansion
title FOODUCK Personal Server Launcher

rem ============================================================
rem Personal launcher only.
rem Does NOT modify team BAT / .env / project source.
rem Automatically finds springboot\mvnw.cmd.
rem Runtime secrets are injected only into the child PowerShell process.
rem ============================================================

set "FOUND_MVNW="

rem 1) BAT location + parents
for /f "usebackq delims=" %%I in (`powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p=[IO.Path]::GetFullPath('%~dp0'); while($p){$c=Join-Path $p 'springboot\mvnw.cmd'; if(Test-Path -LiteralPath $c){$c; break}; $q=[IO.Directory]::GetParent($p); if($null -eq $q){break}; $p=$q.FullName}"`) do set "FOUND_MVNW=%%I"

rem 2) Current directory + parents
if not defined FOUND_MVNW (
  for /f "usebackq delims=" %%I in (`powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
    "$p=[IO.Path]::GetFullPath((Get-Location).Path); while($p){$c=Join-Path $p 'springboot\mvnw.cmd'; if(Test-Path -LiteralPath $c){$c; break}; $q=[IO.Directory]::GetParent($p); if($null -eq $q){break}; $p=$q.FullName}"`) do set "FOUND_MVNW=%%I"
)

rem 3) Common user folders
if not defined FOUND_MVNW (
  for /f "usebackq delims=" %%I in (`powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
    "$roots=@((Join-Path $env:USERPROFILE 'Documents\GitHub'),(Join-Path $env:USERPROFILE 'Desktop'),(Join-Path $env:USERPROFILE 'Documents')); foreach($r in $roots){if(Test-Path -LiteralPath $r){$f=Get-ChildItem -LiteralPath $r -Filter mvnw.cmd -File -Recurse -ErrorAction SilentlyContinue ^| Where-Object {$_.Directory.Name -eq 'springboot'} ^| Select-Object -First 1; if($f){$f.FullName; break}}}"`) do set "FOUND_MVNW=%%I"
)

if not defined FOUND_MVNW (
  echo ============================================================
  echo FOODUCK Personal Server Launcher
  echo ============================================================
  echo [ERROR] springboot\mvnw.cmd was not found.
  echo.
  echo Searched:
  echo   - BAT folder and parent folders
  echo   - Current folder and parent folders
  echo   - Documents\GitHub
  echo   - Desktop
  echo   - Documents
  echo.
  pause
  exit /b 1
)

for %%I in ("%FOUND_MVNW%") do set "APP_DIR=%%~dpI"
for %%I in ("%APP_DIR%..") do set "PROJECT_ROOT=%%~fI"

echo ============================================================
echo FOODUCK Personal Server Launcher v3
echo ============================================================
echo Project root : %PROJECT_ROOT%
echo Spring Boot  : %APP_DIR%
echo DB host      : 192.168.1.185:3306
echo DB name      : foodduck
echo DB user      : foodduck
echo Server port  : 8081
echo.
echo [INFO] Team BAT / .env / source files are NOT modified.
echo [INFO] DB/JWT secrets are injected only into this server process.
echo.

where.exe java.exe >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Java was not found. JDK 17+ is required.
  echo.
  pause
  exit /b 1
)

echo [1/2] Checking TCP connection to MySQL...
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
  "$c=New-Object Net.Sockets.TcpClient; try{$a=$c.BeginConnect('192.168.1.185',3306,$null,$null); if(-not $a.AsyncWaitHandle.WaitOne(3000)){exit 1}; $c.EndConnect($a); exit 0}catch{exit 1}finally{$c.Close()}"

if errorlevel 1 (
  echo.
  echo [ERROR] Cannot reach 192.168.1.185:3306.
  echo Check same Wi-Fi, host MySQL, and firewall.
  echo.
  pause
  exit /b 1
)

echo [OK] MySQL TCP port is reachable.
echo.
echo [2/2] Starting Spring Boot...
echo URL : http://localhost:8081
echo Stop: Ctrl+C
echo.

rem CMD metacharacters in DB password are avoided by building the value in PowerShell.
rem JWT_SECRET and OAUTH_STATE_SECRET are local-only runtime values, not written to the repo.
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
  "$env:DB_URL='jdbc:mysql://192.168.1.185:3306/foodduck?serverTimezone=Asia/Seoul&characterEncoding=UTF-8&useSSL=false&allowPublicKeyRetrieval=true';" ^
  "$env:DB_USERNAME='foodduck';" ^
  "$env:DB_PASSWORD=('aa1234'+[char]94+[char]94);" ^
  "$env:SERVER_PORT='8081';" ^
  "$env:FLYWAY_ENABLED='false';" ^
  "$env:JPA_DDL_AUTO='validate';" ^
  "$env:JWT_SECRET='lRBxLFgbAyCIz8u6jZuob0hDO8RcKBHvt4n2h9bzvswGjvl8flXSRQOTN0kmFz-w';" ^
  "$env:OAUTH_STATE_SECRET='lRBxLFgbAyCIz8u6jZuob0hDO8RcKBHvt4n2h9bzvswGjvl8flXSRQOTN0kmFz-w';" ^
  "Set-Location -LiteralPath '%APP_DIR%';" ^
  "& '%FOUND_MVNW%' 'spring-boot:run';" ^
  "exit $LASTEXITCODE"

set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
  echo.
  echo ============================================================
  echo [ERROR] Spring Boot stopped. Exit code: %EXIT_CODE%
  echo.
  echo Check the FIRST Caused by / Could not resolve placeholder line.
  echo This launcher already supplies:
  echo   DB_URL, DB_USERNAME, DB_PASSWORD
  echo   SERVER_PORT, FLYWAY_ENABLED, JPA_DDL_AUTO
  echo   JWT_SECRET, OAUTH_STATE_SECRET
  echo ============================================================
  pause
)

endlocal & exit /b %EXIT_CODE%
