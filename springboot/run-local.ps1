# .env 파일을 읽어 환경변수로 로드한 뒤 스프링부트를 실행한다.
# 사용법: 프로젝트 루트에서 ./run-local.ps1

$ErrorActionPreference = "Stop"

# 콘솔과 Maven/Spring Boot 로그의 한글 깨짐을 방지한다.
$utf8Encoding = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $utf8Encoding
[Console]::OutputEncoding = $utf8Encoding
$OutputEncoding = $utf8Encoding

# 프로젝트 루트 기준 파일 경로
$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path $envFile)) {
    Write-Error ".env 파일이 없습니다. $envFile 위치에 만들어주세요."
    exit 1
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) { return }
    $idx = $line.IndexOf("=")
    if ($idx -lt 1) { return }
    $key = $line.Substring(0, $idx).Trim()
    $value = $line.Substring($idx + 1)
    [Environment]::SetEnvironmentVariable($key, $value, "Process")
}

Push-Location $PSScriptRoot
& ".\mvnw.cmd" spring-boot:run
$exitCode = $LASTEXITCODE
Pop-Location

exit $mavenExitCode
