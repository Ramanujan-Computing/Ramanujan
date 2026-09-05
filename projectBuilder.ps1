[CmdletBinding()]
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateNotNullOrEmpty()]
    [string]$WorkspacePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Get-RequiredCommand([string]$Name, [string]$InstallHint) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if (-not $command) {
        throw "Required tool '$Name' was not found. $InstallHint"
    }
    return $command.Source
}

function Invoke-CheckedCommand(
    [string]$Command,
    [string[]]$Arguments,
    [string]$FailureMessage
) {
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$FailureMessage (exit code $LASTEXITCODE)."
    }
}

function Get-CMakeTargetArtifacts(
    [string]$BuildDirectory,
    [string[]]$TargetNames
) {
    $replyDirectory = Join-Path $BuildDirectory ".cmake\api\v1\reply"
    if (-not (Test-Path -LiteralPath $replyDirectory -PathType Container)) {
        throw "CMake did not produce its file API reply directory: $replyDirectory"
    }

    $indexFiles = @(Get-ChildItem -LiteralPath $replyDirectory -Filter "index-*.json" -File)
    if ($indexFiles.Count -eq 0) {
        throw "CMake did not produce a file API index in $replyDirectory."
    }
    $indexFile = $indexFiles | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $index = Get-Content -LiteralPath $indexFile.FullName -Raw | ConvertFrom-Json
    $codemodelReply = $index.reply.'codemodel-v2'
    if (-not $codemodelReply) {
        throw "CMake's file API response does not contain codemodel-v2."
    }

    $codemodel = Get-Content -LiteralPath (Join-Path $replyDirectory $codemodelReply.jsonFile) `
        -Raw | ConvertFrom-Json
    $configuration = @($codemodel.configurations) |
        Where-Object { $_.name -eq "Release" } |
        Select-Object -First 1
    if (-not $configuration) {
        $configuration = @($codemodel.configurations) | Select-Object -First 1
    }
    if (-not $configuration) {
        throw "CMake's codemodel does not contain a build configuration."
    }

    $artifacts = @()
    foreach ($targetName in $TargetNames) {
        $targetReferences = @($configuration.targets | Where-Object { $_.name -eq $targetName })
        if ($targetReferences.Count -ne 1) {
            throw "Expected exactly one CMake target named '$targetName', but found $($targetReferences.Count)."
        }

        $target = Get-Content -LiteralPath (Join-Path $replyDirectory $targetReferences[0].jsonFile) `
            -Raw | ConvertFrom-Json
        $targetArtifacts = @($target.artifacts |
                ForEach-Object { [IO.Path]::GetFullPath((Join-Path $BuildDirectory $_.path)) } |
                Where-Object { [IO.Path]::GetExtension($_) -in @(".dll", ".exe") })
        if ($targetArtifacts.Count -ne 1) {
            $found = if ($targetArtifacts.Count -eq 0) { "none" } else { $targetArtifacts -join ", " }
            throw "Expected exactly one Windows artifact for CMake target '$targetName', but found $($targetArtifacts.Count): $found"
        }
        $artifacts += $targetArtifacts[0]
    }

    return @($artifacts | Select-Object -Unique)
}

$repositoryRoot = (Resolve-Path -LiteralPath $PSScriptRoot).Path
$workspace = [System.IO.Path]::GetFullPath($WorkspacePath)

if (Test-Path -LiteralPath $workspace -PathType Leaf) {
    throw "The workspace path points to a file: $workspace"
}
New-Item -ItemType Directory -Path $workspace -Force | Out-Null
$workspace = (Resolve-Path -LiteralPath $workspace).Path
$env:RAMANUJAN_WS = $workspace

$maven = Get-RequiredCommand "mvn" "Install Maven 3.9 or newer and add it to PATH."
Get-RequiredCommand "git" "Install Git for Windows and add it to PATH." | Out-Null

$mavenModules = @(
    "commons",
    "rule-engine",
    "ramanujan-device-common",
    "developer-console-model",
    "monitoring-utils2",
    "db-layer",
    "kafka-manager",
    "orchestrator",
    "middleware",
    "developer-console"
)

foreach ($module in $mavenModules) {
    $moduleDirectory = Join-Path $repositoryRoot $module
    if (-not (Test-Path -LiteralPath (Join-Path $moduleDirectory "pom.xml") -PathType Leaf)) {
        throw "Maven module '$module' is missing its pom.xml at $moduleDirectory."
    }

    Write-Host "Building Maven module: $module"
    Invoke-CheckedCommand $maven @("-f", (Join-Path $moduleDirectory "pom.xml"), "clean", "install") `
        "Maven build failed for module '$module'"
}

$nativeSource = Join-Path $repositoryRoot "ramanujan-native\native"
$pathBytes = [Text.Encoding]::UTF8.GetBytes($repositoryRoot)
$sha256 = [Security.Cryptography.SHA256]::Create()
try {
    $pathHash = ([BitConverter]::ToString($sha256.ComputeHash($pathBytes))).Replace("-", "").Substring(0, 12)
}
finally {
    $sha256.Dispose()
}
$nativeBuild = Join-Path ([IO.Path]::GetTempPath()) "ramanujan-native-build-$pathHash"
$windowsNativeBuilder = Join-Path $nativeSource "buildWindows.ps1"
if (-not (Test-Path -LiteralPath $windowsNativeBuilder -PathType Leaf)) {
    throw "Windows native build script was not found: $windowsNativeBuilder"
}

$fileApiQuery = Join-Path $nativeBuild ".cmake\api\v1\query\codemodel-v2"
New-Item -ItemType Directory -Path (Split-Path $fileApiQuery -Parent) -Force | Out-Null
New-Item -ItemType File -Path $fileApiQuery -Force | Out-Null

Write-Host "Configuring and building the Windows desktop native component"
& $windowsNativeBuilder -BuildDirectory $nativeBuild
if ($LASTEXITCODE -ne 0) {
    throw "Windows native build failed (exit code $LASTEXITCODE)."
}

$fatJars = @(Get-ChildItem -LiteralPath (Join-Path $repositoryRoot "developer-console\target") `
        -Filter "developer-console-*-fat.jar" -File)
if ($fatJars.Count -ne 1) {
    $found = if ($fatJars.Count -eq 0) { "none" } else { $fatJars.FullName -join ", " }
    throw "Expected exactly one developer-console fat JAR, but found $($fatJars.Count): $found"
}

$nativeArtifacts = @(Get-CMakeTargetArtifacts $nativeBuild @("native", "native_lib"))
$artifacts = @($fatJars[0].FullName) + $nativeArtifacts
foreach ($artifact in $artifacts) {
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
        throw "CMake reported a native artifact that does not exist: $artifact"
    }
    Copy-Item -LiteralPath $artifact -Destination (Join-Path $workspace (Split-Path $artifact -Leaf)) -Force
}

Write-Host "Ramanujan build completed. RAMANUJAN_WS=$workspace"
Write-Host "Staged artifacts:"
$artifacts | ForEach-Object { Write-Host "  $(Join-Path $workspace (Split-Path $_ -Leaf))" }
