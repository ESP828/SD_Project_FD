# .env 파일을 현재 프로세스 환경변수로 로드한 뒤 Spring Boot를 실행한다.
# 사용법:
#   저장소 루트: ./springboot/run-local.ps1
#   springboot 폴더: ./run-local.ps1

$ErrorActionPreference = "Stop"

$utf8Encoding = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $utf8Encoding
[Console]::OutputEncoding = $utf8Encoding
$OutputEncoding = $utf8Encoding

$envFile = Join-Path $PSScriptRoot ".env"
if (-not (Test-Path -LiteralPath $envFile)) {
    Write-Error ".env 파일이 없습니다. $envFile 위치에 만들어주세요."
    exit 1
}

$loadedKeys = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase
)

Get-Content -LiteralPath $envFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq "" -or $line.StartsWith("#")) {
        return
    }

    if ($line.StartsWith("export ")) {
        $line = $line.Substring(7).TrimStart()
    }

    $separatorIndex = $line.IndexOf("=")
    if ($separatorIndex -lt 1) {
        return
    }

    $key = $line.Substring(0, $separatorIndex).Trim()
    if ($key -notmatch "^[A-Za-z_][A-Za-z0-9_]*$") {
        throw ".env 환경변수 이름이 올바르지 않습니다: $key"
    }

    $value = $line.Substring($separatorIndex + 1).Trim()
    if ($value.Length -ge 2) {
        $isDoubleQuoted = $value.StartsWith('"') -and $value.EndsWith('"')
        $isSingleQuoted = $value.StartsWith("'") -and $value.EndsWith("'")
        if ($isDoubleQuoted -or $isSingleQuoted) {
            $value = $value.Substring(1, $value.Length - 2)
        }
    }

    [Environment]::SetEnvironmentVariable($key, $value, "Process")
    [void] $loadedKeys.Add($key)
}

$requiredKeys = @(
    "DB_URL",
    "DB_USERNAME",
    "DB_PASSWORD",
    "JWT_SECRET"
)

$missingKeys = @(
    $requiredKeys | Where-Object {
        [string]::IsNullOrWhiteSpace(
            [Environment]::GetEnvironmentVariable($_, "Process")
        )
    }
)

if ($missingKeys.Count -gt 0) {
    Write-Error (
        ".env 필수 환경변수가 비어 있습니다: " +
        ($missingKeys -join ", ")
    )
    exit 1
}

if ([string]::IsNullOrWhiteSpace($env:KAKAO_REST_API_KEY)) {
    Write-Warning "KAKAO_REST_API_KEY가 없어 Kakao 로그인을 사용할 수 없습니다."
}

$serverPort = $env:SERVER_PORT
if ([string]::IsNullOrWhiteSpace($serverPort)) {
    $serverPort = "8081"
}

Write-Host "환경변수 $($loadedKeys.Count)개를 불러왔습니다."
Write-Host "FOODUCK 서버 시작: http://localhost:$serverPort/"
Write-Host "서버를 종료하려면 Ctrl+C를 누르세요."
Write-Host ""

Push-Location $PSScriptRoot
try {
    $jvmArguments = (
        "-Dfile.encoding=UTF-8 " +
        "-Dsun.stdout.encoding=UTF-8 " +
        "-Dsun.stderr.encoding=UTF-8"
    )
    & "$PSScriptRoot\mvnw.cmd" `
        "-Dspring-boot.run.jvmArguments=$jvmArguments" `
        spring-boot:run
    $mavenExitCode = $LASTEXITCODE
}
finally {
    Pop-Location
}

if ($null -eq $mavenExitCode) {
    $mavenExitCode = 1
}

exit $mavenExitCode
