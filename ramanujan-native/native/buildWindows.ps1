param(
    [string]$BuildDirectory = "C:\ramanujan-native-build",
    [switch]$GpuEnabled
)

$ErrorActionPreference = "Stop"

$vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
if (-not (Test-Path $vswhere)) {
    throw "Visual Studio Installer (vswhere.exe) was not found."
}

$visualStudio = & $vswhere -latest -products * `
    -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
    -property installationPath
if (-not $visualStudio) {
    throw "Visual Studio with the Desktop development with C++ workload is required."
}

$vcvars = Join-Path $visualStudio "VC\Auxiliary\Build\vcvars64.bat"
$cmake = Join-Path $visualStudio "Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
$ninja = Join-Path $visualStudio "Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja"
if (-not (Test-Path $cmake)) {
    throw "CMake was not found in the Visual Studio installation."
}

$jdkHome = $env:JAVA_HOME
if (-not $jdkHome -or -not (Test-Path (Join-Path $jdkHome "bin\javac.exe"))) {
    $jdk = Get-ChildItem "$env:ProgramFiles\Microsoft\jdk-*" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
    $jdkHome = if ($jdk) { $jdk.FullName } else { $null }
}
if (-not $jdkHome) {
    throw "Microsoft OpenJDK 17 is required. Install it with: winget install Microsoft.OpenJDK.17"
}

$env:Path = "$(Split-Path $vswhere);$env:Path"
cmd.exe /s /c "`"$vcvars`" >nul && set" |
    ForEach-Object {
        if ($_ -match "^([^=]+)=(.*)$") {
            Set-Item -Path "env:$($matches[1])" -Value $matches[2]
        }
    }

$env:JAVA_HOME = $jdkHome
$env:JDK_HOME = $jdkHome
$env:Path = "$ninja;$env:Path"
$gpu = if ($GpuEnabled) { "ON" } else { "OFF" }

& $cmake -S $PSScriptRoot -B $BuildDirectory -G Ninja `
    -DCMAKE_BUILD_TYPE=Release "-DGPU_ENABLED=$gpu"
if ($LASTEXITCODE -ne 0) {
    throw "CMake configuration failed."
}

& $cmake --build $BuildDirectory --config Release --parallel
if ($LASTEXITCODE -ne 0) {
    throw "Native build failed."
}

Write-Host "Windows native build completed: $BuildDirectory\native_test.exe and $BuildDirectory\native.dll"
