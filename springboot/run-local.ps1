# Loads local configuration, starts FastAPI and Spring Boot in this console,
# and supervises both processes until Ctrl+C is pressed.

[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

$utf8Encoding = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $utf8Encoding
[Console]::OutputEncoding = $utf8Encoding
$OutputEncoding = $utf8Encoding

$appDirectory = $PSScriptRoot
$aiDirectory = Join-Path $appDirectory "ai"
$envFile = Join-Path $appDirectory ".env"
$requirementsFile = Join-Path $aiDirectory "requirements.txt"
$fastApiApplication = Join-Path $aiDirectory "app.py"
$mavenWrapper = Join-Path $appDirectory "mvnw.cmd"
$fastApiPort = 8000
$springBootPort = 8081

function Import-EnvironmentFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    Get-Content -LiteralPath $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq "" -or $line.StartsWith("#")) {
            return
        }

        $separatorIndex = $line.IndexOf("=")
        if ($separatorIndex -lt 1) {
            return
        }

        $key = $line.Substring(0, $separatorIndex).Trim()
        $value = $line.Substring($separatorIndex + 1)
        [Environment]::SetEnvironmentVariable($key, $value, "Process")
    }
}

function Test-ControlCPressed {
    try {
        while ([Console]::KeyAvailable) {
            $key = [Console]::ReadKey($true)
            $controlPressed = ($key.Modifiers -band [ConsoleModifiers]::Control) -ne 0
            if ($controlPressed -and $key.Key -eq [ConsoleKey]::C) {
                return $true
            }
        }
    }
    catch {
        return $false
    }

    return $false
}

function Resolve-PythonRuntime {
    $candidates = @()
    $projectCandidates = @(
        (Join-Path $aiDirectory ".venv\Scripts\python.exe"),
        (Join-Path $appDirectory ".venv\Scripts\python.exe"),
        (Join-Path $appDirectory "venv\Scripts\python.exe"),
        (Join-Path $appDirectory "env\Scripts\python.exe")
    )

    if (-not [string]::IsNullOrWhiteSpace($env:VIRTUAL_ENV)) {
        $projectCandidates = @((Join-Path $env:VIRTUAL_ENV "Scripts\python.exe")) + $projectCandidates
    }
    if (-not [string]::IsNullOrWhiteSpace($env:PYTHON)) {
        $projectCandidates = @($env:PYTHON) + $projectCandidates
    }

    foreach ($candidatePath in $projectCandidates) {
        if (Test-Path -LiteralPath $candidatePath -PathType Leaf) {
            $candidates += [PSCustomObject]@{ FilePath = $candidatePath; PrefixArguments = @() }
        }
    }

    $pathPython = Get-Command python.exe -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $pathPython) {
        $candidates += [PSCustomObject]@{ FilePath = $pathPython.Source; PrefixArguments = @() }
    }

    $pythonLauncher = Get-Command py.exe -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -ne $pythonLauncher) {
        $candidates += [PSCustomObject]@{ FilePath = $pythonLauncher.Source; PrefixArguments = @("-3") }
    }

    if (-not [string]::IsNullOrWhiteSpace($env:LOCALAPPDATA)) {
        $localPythonRoot = Join-Path $env:LOCALAPPDATA "Programs\Python"
        if (Test-Path -LiteralPath $localPythonRoot -PathType Container) {
            Get-ChildItem -LiteralPath $localPythonRoot -Directory -ErrorAction SilentlyContinue |
                Sort-Object Name -Descending |
                ForEach-Object {
                    $pythonExecutable = Join-Path $_.FullName "python.exe"
                    if (Test-Path -LiteralPath $pythonExecutable -PathType Leaf) {
                        $candidates += [PSCustomObject]@{ FilePath = $pythonExecutable; PrefixArguments = @() }
                    }
                }
        }
    }

    foreach ($programFilesRoot in @($env:ProgramFiles, ${env:ProgramFiles(x86)})) {
        if ([string]::IsNullOrWhiteSpace($programFilesRoot)) {
            continue
        }

        Get-ChildItem -Path (Join-Path $programFilesRoot "Python*") -Directory -ErrorAction SilentlyContinue |
            Sort-Object Name -Descending |
            ForEach-Object {
                $pythonExecutable = Join-Path $_.FullName "python.exe"
                if (Test-Path -LiteralPath $pythonExecutable -PathType Leaf) {
                    $candidates += [PSCustomObject]@{ FilePath = $pythonExecutable; PrefixArguments = @() }
                }
            }
    }

    $seenCandidates = @{}
    foreach ($candidate in $candidates) {
        $prefixArguments = @($candidate.PrefixArguments)
        $candidateKey = "$($candidate.FilePath)|$($prefixArguments -join ' ')"
        if ($seenCandidates.ContainsKey($candidateKey)) {
            continue
        }
        $seenCandidates[$candidateKey] = $true

        try {
            $versionCheckArguments = $prefixArguments + @(
                "-c",
                "import sys; raise SystemExit(0 if sys.version_info >= (3, 10) else 1)"
            )
            & $candidate.FilePath @versionCheckArguments *> $null
            if ($LASTEXITCODE -eq 0) {
                return $candidate
            }
        }
        catch {
            continue
        }
    }

    throw "Python 3.10+ was not found. Install Python or set PYTHON in springboot\.env."
}

function Test-PythonDependencies {
    param([Parameter(Mandatory = $true)]$Runtime)

    $dependencyCheck = @(
        "import cryptography",
        "import fastapi",
        "import numpy",
        "import pandas",
        "import pymysql",
        "import sentence_transformers",
        "import sklearn",
        "import sqlalchemy",
        "import torch",
        "import uvicorn"
    ) -join "; "
    $arguments = @($Runtime.PrefixArguments) + @("-c", $dependencyCheck)
    & $Runtime.FilePath @arguments *> $null
    return $LASTEXITCODE -eq 0
}

function Install-PythonDependencies {
    param([Parameter(Mandatory = $true)]$Runtime)

    $pipCheckArguments = @($Runtime.PrefixArguments) + @("-m", "pip", "--version")
    & $Runtime.FilePath @pipCheckArguments *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "pip is not available for the detected Python runtime: $($Runtime.FilePath)"
    }

    Write-Host "[1/4] Installing missing FastAPI dependencies..."
    $pipInstallArguments = @($Runtime.PrefixArguments) + @(
        "-m",
        "pip",
        "install",
        "--disable-pip-version-check",
        "--quiet",
        "--requirement",
        $requirementsFile
    )
    & $Runtime.FilePath @pipInstallArguments
    if ($LASTEXITCODE -ne 0) {
        throw "Python dependency installation failed with exit code $LASTEXITCODE."
    }
}

function Invoke-AiModelPreparation {
    param([Parameter(Mandatory = $true)]$Runtime)

    if ($env:FOODUCK_SKIP_AI_MODEL_PREPARATION -eq "true") {
        Write-Host "[2/4] AI model preparation skipped by environment setting."
        return
    }

    $buildEmbeddingsScript = Join-Path $aiDirectory "build_embeddings.py"
    $trainingScript = Join-Path $aiDirectory "train.py"
    $scriptToRun = $null
    $stepDescription = $null

    if (Test-Path -LiteralPath $buildEmbeddingsScript -PathType Leaf) {
        $scriptToRun = $buildEmbeddingsScript
        $stepDescription = "Refreshing AI recommendation data"
    }
    elseif (Test-Path -LiteralPath $trainingScript -PathType Leaf) {
        $scriptToRun = $trainingScript
        $stepDescription = "Training the AI model"
    }
    else {
        Write-Warning "No AI model preparation script was found."
        return
    }

    Write-Host "[2/4] $stepDescription..."
    Push-Location $aiDirectory
    try {
        $scriptArguments = @($Runtime.PrefixArguments) + @($scriptToRun)
        & $Runtime.FilePath @scriptArguments
        $preparationExitCode = $LASTEXITCODE
    }
    catch {
        Write-Warning "AI model preparation failed: $($_.Exception.Message)"
        return
    }
    finally {
        Pop-Location
    }

    if ($preparationExitCode -ne 0) {
        Write-Warning "AI model preparation exited with code $preparationExitCode. Starting with existing model files."
    }
}

function Test-TcpPortInUse {
    param([Parameter(Mandatory = $true)][int]$Port)

    try {
        return [System.Net.NetworkInformation.IPGlobalProperties]::GetIPGlobalProperties().GetActiveTcpListeners().Port -contains $Port
    }
    catch {
        return $false
    }
}

function Test-ChildProcessRunning {
    param($ChildProcess)

    if ($null -eq $ChildProcess) {
        return $false
    }

    try {
        $ChildProcess.Refresh()
        return -not $ChildProcess.HasExited
    }
    catch {
        return $false
    }
}

function Stop-ChildProcessTree {
    param($ChildProcess)

    if (-not (Test-ChildProcessRunning -ChildProcess $ChildProcess)) {
        return
    }

    $processId = $ChildProcess.Id
    $taskKill = Join-Path $env:SystemRoot "System32\taskkill.exe"
    $previousErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "SilentlyContinue"
        & $taskKill /PID $processId /T /F *> $null
        $taskKillExitCode = $LASTEXITCODE
    }
    finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }

    if ($taskKillExitCode -ne 0 -and (Test-ChildProcessRunning -ChildProcess $ChildProcess)) {
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }
}

function Stop-ServerProcesses {
    param($FastApiProcess, $SpringProcess)

    $childProcesses = @($FastApiProcess, $SpringProcess) | Where-Object { $null -ne $_ }
    foreach ($childProcess in $childProcesses) {
        Stop-ChildProcessTree -ChildProcess $childProcess
    }
}

$fastApiProcess = $null
$springProcess = $null
$controlCAsInputEnabled = $false
$originalTreatControlCAsInput = $false
$userRequestedStop = $false
$exitCode = 0

try {
    foreach ($requiredPath in @($envFile, $requirementsFile, $fastApiApplication, $mavenWrapper)) {
        if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
            throw "Required file was not found: $requiredPath"
        }
    }

    Import-EnvironmentFile -Path $envFile

    if (Test-TcpPortInUse -Port $fastApiPort) {
        throw "Port $fastApiPort is already in use. Stop the existing process and try again."
    }
    if (Test-TcpPortInUse -Port $springBootPort) {
        throw "Port $springBootPort is already in use. Stop the existing process and try again."
    }

    $pythonRuntime = Resolve-PythonRuntime
    $pythonDisplayArguments = @($pythonRuntime.PrefixArguments) + @(
        "-c",
        "import sys; print(f'{sys.executable} (Python {sys.version.split()[0]})')"
    )
    $pythonDescription = (& $pythonRuntime.FilePath @pythonDisplayArguments | Select-Object -First 1)
    Write-Host "[1/4] Python runtime: $pythonDescription"

    if (-not (Test-PythonDependencies -Runtime $pythonRuntime)) {
        Install-PythonDependencies -Runtime $pythonRuntime
        if (-not (Test-PythonDependencies -Runtime $pythonRuntime)) {
            throw "FastAPI dependencies are still unavailable after installation."
        }
    }

    Invoke-AiModelPreparation -Runtime $pythonRuntime

    $targetDirectory = Join-Path $appDirectory "target"
    if (Test-Path -LiteralPath $targetDirectory -PathType Container) {
        Get-ChildItem -LiteralPath $targetDirectory -Recurse -Force -File |
            Where-Object { $_.IsReadOnly } |
            ForEach-Object { $_.IsReadOnly = $false }
    }

    $env:PYTHONUNBUFFERED = "1"
    $env:PYTHONIOENCODING = "utf-8"
    if ([Console]::IsInputRedirected) {
        throw "Interactive console input is required so Ctrl+C can stop both servers."
    }
    $originalTreatControlCAsInput = [Console]::TreatControlCAsInput
    [Console]::TreatControlCAsInput = $true
    $controlCAsInputEnabled = $true

    Write-Host "[3/4] Starting FastAPI on http://127.0.0.1:$fastApiPort ..."
    $fastApiArguments = @($pythonRuntime.PrefixArguments) + @(
        "-m",
        "uvicorn",
        "app:app",
        "--host",
        "127.0.0.1",
        "--port",
        $fastApiPort.ToString()
    )
    $fastApiProcess = Start-Process `
        -FilePath $pythonRuntime.FilePath `
        -ArgumentList $fastApiArguments `
        -WorkingDirectory $aiDirectory `
        -NoNewWindow `
        -PassThru

    Write-Host "[4/4] Starting Spring Boot on http://localhost:$springBootPort ..."
    $commandProcessor = if ([string]::IsNullOrWhiteSpace($env:ComSpec)) {
        Join-Path $env:SystemRoot "System32\cmd.exe"
    }
    else {
        $env:ComSpec
    }
    $springProcess = Start-Process `
        -FilePath $commandProcessor `
        -ArgumentList @("/d", "/s", "/c", "mvnw.cmd spring-boot:run -Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8") `
        -WorkingDirectory $appDirectory `
        -NoNewWindow `
        -PassThru

    Write-Host ""
    Write-Host "Both servers are running in this console. Press Ctrl+C once to stop both."
    Write-Host ""

    $fastApiReady = $false
    $springBootReady = $false
    while ($true) {
        if (Test-ControlCPressed) {
            $userRequestedStop = $true
            break
        }

        if (-not (Test-ChildProcessRunning -ChildProcess $fastApiProcess)) {
            throw "FastAPI stopped unexpectedly with exit code $($fastApiProcess.ExitCode)."
        }
        if (-not (Test-ChildProcessRunning -ChildProcess $springProcess)) {
            throw "Spring Boot stopped unexpectedly with exit code $($springProcess.ExitCode)."
        }

        if (-not $fastApiReady -and (Test-TcpPortInUse -Port $fastApiPort)) {
            $fastApiReady = $true
            Write-Host "[READY] FastAPI is listening on port $fastApiPort."
        }
        if (-not $springBootReady -and (Test-TcpPortInUse -Port $springBootPort)) {
            $springBootReady = $true
            Write-Host "[READY] Spring Boot is listening on port $springBootPort."
        }

        Start-Sleep -Milliseconds 250
    }
}
catch {
    $exitCode = 1
    Write-Host ""
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    if ($null -ne $fastApiProcess -or $null -ne $springProcess) {
        if ($userRequestedStop) {
            Write-Host ""
            Write-Host "Ctrl+C received. Stopping FastAPI and Spring Boot..."
        }
        else {
            Write-Host "Stopping remaining server processes..."
        }

        Stop-ServerProcesses `
            -FastApiProcess $fastApiProcess `
            -SpringProcess $springProcess
        Write-Host "All FOODUCK server processes have stopped."
    }

    if ($controlCAsInputEnabled) {
        [Console]::TreatControlCAsInput = $originalTreatControlCAsInput
    }
}

if ($userRequestedStop) {
    exit 0
}

exit $exitCode
