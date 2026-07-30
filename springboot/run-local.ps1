# .env 파일을 읽어 환경변수로 로드한 뒤 Spring Boot를 실행한다.
# 사용법: 프로젝트 루트에서 .\run-local.ps1

$ErrorActionPreference = "Stop"

# 콘솔과 Maven/Spring Boot 로그의 한글 깨짐을 방지한다.
$utf8Encoding = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $utf8Encoding
[Console]::OutputEncoding = $utf8Encoding
$OutputEncoding = $utf8Encoding

# 프로젝트 루트 기준 파일 경로
$envFile = Join-Path $PSScriptRoot ".env"
$mavenWrapper = Join-Path $PSScriptRoot "mvnw.cmd"
$pomFile = Join-Path $PSScriptRoot "pom.xml"

# .env 존재 여부 확인
if (-not (Test-Path -LiteralPath $envFile)) {
    Write-Host (
        ".env 파일이 없습니다. 다음 위치에 만들어주세요: $envFile"
    ) -ForegroundColor Red
    exit 1
}

# Maven Wrapper 존재 여부 확인
if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    Write-Host (
        "mvnw.cmd 파일이 없습니다: $mavenWrapper"
    ) -ForegroundColor Red
    exit 1
}

# pom.xml 존재 여부 확인
if (-not (Test-Path -LiteralPath $pomFile)) {
    Write-Host (
        "pom.xml 파일이 없습니다: $pomFile"
    ) -ForegroundColor Red
    exit 1
}

# .env에서 실제로 불러온 키를 기록한다.
$loadedKeys = [System.Collections.Generic.HashSet[string]]::new(
    [System.StringComparer]::OrdinalIgnoreCase
)

# .env 파일 읽기
Get-Content -LiteralPath $envFile -Encoding UTF8 | ForEach-Object {
    $line = $_.Trim()

    # 빈 줄과 주석은 건너뛴다.
    if ($line -eq "" -or $line.StartsWith("#")) {
        return
    }

    # export KEY=value 형식도 지원한다.
    if ($line.StartsWith("export ")) {
        $line = $line.Substring(7).TrimStart()
    }

    # 첫 번째 = 문자를 기준으로 키와 값을 나눈다.
    $separatorIndex = $line.IndexOf("=")

    if ($separatorIndex -lt 1) {
        return
    }

    $key = $line.Substring(0, $separatorIndex).Trim()

    # 정상적인 환경변수 이름인지 확인한다.
    if ($key -notmatch "^[A-Za-z_][A-Za-z0-9_]*$") {
        throw ".env 환경변수 이름이 올바르지 않습니다: $key"
    }

    $value = $line.Substring($separatorIndex + 1).Trim()

    # 값 전체를 따옴표로 감싼 경우 바깥 따옴표를 제거한다.
    if ($value.Length -ge 2) {
        $isDoubleQuoted = (
            $value.StartsWith('"') -and
            $value.EndsWith('"')
        )

        $isSingleQuoted = (
            $value.StartsWith("'") -and
            $value.EndsWith("'")
        )

        if ($isDoubleQuoted -or $isSingleQuoted) {
            $value = $value.Substring(
                1,
                $value.Length - 2
            )
        }
    }

    # 현재 실행 중인 PowerShell 프로세스에 환경변수를 등록한다.
    [Environment]::SetEnvironmentVariable(
        $key,
        $value,
        "Process"
    )

    [void]$loadedKeys.Add($key)
}

# 반드시 필요한 환경변수
$requiredKeys = @(
    "DB_URL",
    "DB_USERNAME",
    "DB_PASSWORD",
    "JWT_SECRET"
)

$missingKeys = @(
    $requiredKeys | Where-Object {
        [string]::IsNullOrWhiteSpace(
            [Environment]::GetEnvironmentVariable(
                $_,
                "Process"
            )
        )
    }
)

if ($missingKeys.Count -gt 0) {
    Write-Host (
        ".env 필수 환경변수가 비어 있습니다: " +
        ($missingKeys -join ", ")
    ) -ForegroundColor Red

    exit 1
}

# 카카오 로그인 키는 선택 항목으로 처리한다.
if ([string]::IsNullOrWhiteSpace($env:KAKAO_REST_API_KEY)) {
    Write-Warning (
        "KAKAO_REST_API_KEY가 없어 " +
        "Kakao 로그인을 사용할 수 없습니다."
    )
}

# SERVER_PORT가 없으면 8081을 기본값으로 사용한다.
$serverPort = $env:SERVER_PORT

if ([string]::IsNullOrWhiteSpace($serverPort)) {
    $serverPort = "8081"

    # Spring Boot에서도 같은 포트를 읽도록 환경변수로 등록한다.
    [Environment]::SetEnvironmentVariable(
        "SERVER_PORT",
        $serverPort,
        "Process"
    )
}

Write-Host "환경변수 $($loadedKeys.Count)개를 불러왔습니다."
Write-Host "FOODUCK 서버 시작: http://localhost:$serverPort/"
Write-Host "서버를 종료하려면 Ctrl+C를 누르세요."
Write-Host ""

$mavenExitCode = 1

Push-Location $PSScriptRoot

try {
    $jvmArguments = (
        "-Dfile.encoding=UTF-8 " +
        "-Dsun.stdout.encoding=UTF-8 " +
        "-Dsun.stderr.encoding=UTF-8"
    )

    & $mavenWrapper `
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
