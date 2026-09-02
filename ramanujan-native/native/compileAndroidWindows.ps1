param(
    [string]$NdkPath,
    [string[]]$Abis = @("arm64-v8a"),
    [ValidatePattern("^android-[0-9]+$")]
    [string]$AndroidPlatform = "android-21",
    [string]$BuildDirectory = "C:\ramanujan-android-build",
    [string]$ProtocExecutable,
    [switch]$DisableGpu
)

$ErrorActionPreference = "Stop"

function Find-Executable([string]$Name, [string]$VisualStudioRelativePath) {
    $command = Get-Command $Name -ErrorAction SilentlyContinue
    if ($command) {
        return $command.Source
    }

    $vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
    if (Test-Path $vswhere) {
        $visualStudio = & $vswhere -latest -products * -property installationPath
        if ($visualStudio) {
            $candidate = Join-Path $visualStudio $VisualStudioRelativePath
            if (Test-Path $candidate) {
                return $candidate
            }
        }
    }
    throw "$Name was not found. Install CMake and Ninja or Visual Studio with Desktop development with C++."
}

function Import-VisualStudioEnvironment {
    $vswhere = Join-Path ${env:ProgramFiles(x86)} "Microsoft Visual Studio\Installer\vswhere.exe"
    if (-not (Test-Path $vswhere)) {
        return
    }
    $visualStudio = & $vswhere -latest -products * `
        -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 `
        -property installationPath
    if (-not $visualStudio) {
        return
    }

    $vcvars = Join-Path $visualStudio "VC\Auxiliary\Build\vcvars64.bat"
    cmd.exe /s /c "`"$vcvars`" >nul && set" |
        ForEach-Object {
            if ($_ -match "^([^=]+)=(.*)$") {
                Set-Item -Path "env:$($matches[1])" -Value $matches[2]
            }
        }
}

if (-not $NdkPath) {
    $NdkPath = $env:ANDROID_NDK_HOME
}
if (-not $NdkPath) {
    $NdkPath = $env:ANDROID_NDK_ROOT
}
if (-not $NdkPath) {
    $ndkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk\ndk"
    $latestNdk = Get-ChildItem $ndkRoot -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($latestNdk) {
        $NdkPath = $latestNdk.FullName
    }
}

if (-not $NdkPath) {
    throw "Android NDK not found. Pass -NdkPath or set ANDROID_NDK_HOME."
}
$NdkPath = (Resolve-Path $NdkPath).Path
$toolchain = Join-Path $NdkPath "build\cmake\android.toolchain.cmake"
if (-not (Test-Path $toolchain)) {
    throw "Invalid Android NDK path: $toolchain does not exist."
}

$supportedAbis = @("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
foreach ($abi in $Abis) {
    if ($abi -notin $supportedAbis) {
        throw "Unsupported ABI '$abi'. Valid values: $($supportedAbis -join ', ')."
    }
}

$cmake = Find-Executable "cmake" "Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe"
$ninja = Find-Executable "ninja" "Common7\IDE\CommonExtensions\Microsoft\CMake\Ninja\ninja.exe"
$env:Path = "$(Split-Path $ninja);$env:Path"

if (-not $env:JAVA_HOME) {
    $jdk = Get-ChildItem "$env:ProgramFiles\Microsoft\jdk-*" -Directory -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1
    if ($jdk) {
        $env:JAVA_HOME = $jdk.FullName
    }
}
if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME is not set and no Microsoft OpenJDK installation was found."
}
$env:JDK_HOME = $env:JAVA_HOME

Import-VisualStudioEnvironment

if (-not $ProtocExecutable) {
    $protocCandidates = @(
        (Join-Path $BuildDirectory "host\_deps\protobuf-build\protoc.exe"),
        (Join-Path $PSScriptRoot "build-host\_deps\protobuf-build\protoc.exe"),
        "C:\rj-native-build\_deps\protobuf-build\protoc.exe"
    )
    $ProtocExecutable = $protocCandidates |
        Where-Object { Test-Path $_ } |
        Select-Object -First 1
}

if (-not $ProtocExecutable) {
    $hostBuild = Join-Path $BuildDirectory "host"
    Write-Host "Building host protoc compiler..."
    & $cmake -S $PSScriptRoot -B $hostBuild -G Ninja `
        -DCMAKE_BUILD_TYPE=Release -DGPU_ENABLED=OFF
    if ($LASTEXITCODE -ne 0) {
        throw "Host protoc configuration failed."
    }
    & $cmake --build $hostBuild --target protoc --parallel
    if ($LASTEXITCODE -ne 0) {
        throw "Host protoc build failed."
    }
    $ProtocExecutable = Join-Path $hostBuild "_deps\protobuf-build\protoc.exe"
}

$ProtocExecutable = (Resolve-Path $ProtocExecutable).Path
$gpuEnabled = if ($DisableGpu) { "OFF" } else { "ON" }
$jniLibs = Join-Path $PSScriptRoot "..\..\androidapp\app\src\main\jniLibs"

foreach ($abi in $Abis) {
    $abiBuild = Join-Path $BuildDirectory $abi
    Write-Host "Building Android ABI $abi (GPU_ENABLED=$gpuEnabled)..."
    & $cmake -S $PSScriptRoot -B $abiBuild -G Ninja `
        -DCMAKE_BUILD_TYPE=Release `
        "-DCMAKE_TOOLCHAIN_FILE=$toolchain" `
        "-DANDROID_ABI=$abi" `
        "-DANDROID_PLATFORM=$AndroidPlatform" `
        "-DGPU_ENABLED=$gpuEnabled" `
        "-DPROTOC_EXECUTABLE=$ProtocExecutable" `
        -Dprotobuf_BUILD_PROTOC_BINARIES=OFF
    if ($LASTEXITCODE -ne 0) {
        throw "CMake configuration failed for $abi."
    }

    & $cmake --build $abiBuild --target native_lib --parallel
    if ($LASTEXITCODE -ne 0) {
        throw "Native Android build failed for $abi."
    }

    $library = @(
        (Join-Path $abiBuild "libnative.so"),
        (Join-Path $abiBuild "lib\libnative.so")
    ) | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $library) {
        throw "libnative.so was not found for $abi."
    }

    $destination = Join-Path $jniLibs $abi
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    Copy-Item $library (Join-Path $destination "libnative.so") -Force
    Write-Host "Copied libnative.so to $destination"
}

Write-Host "Android native build completed for: $($Abis -join ', ')"
