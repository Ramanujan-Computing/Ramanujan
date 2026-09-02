# Monte Carlo Pi

`monte_carlo_pi.py` estimates pi using four parallel Ramanujan threads. Each
thread samples 2,000,000 points, and the final DAG node combines all four
results.

## macOS and Ubuntu Setup

Run these commands from the repository root:

```sh
export RAMANUJAN_WS="$HOME/ramanujan-ws"
mkdir -p "$RAMANUJAN_WS"

python3 -m pip install ast2json
chmod +x projectBuilder.sh
./projectBuilder.sh

cp developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar \
    "$RAMANUJAN_WS/"

# macOS
cp ramanujan-native/native/build/libnative.dylib "$RAMANUJAN_WS/"

# Ubuntu
cp ramanujan-native/native/build/libnative.so "$RAMANUJAN_WS/"
```

Add the following to `~/.zshrc` on macOS or `~/.bashrc` on Ubuntu:

```sh
export RAMANUJAN_WS="$HOME/ramanujan-ws"
alias rj='java -Xms64m -Xmx512m -jar "$RAMANUJAN_WS/developer-console-1.0-SNAPSHOT-fat.jar"'
```

Reload the applicable profile with `source ~/.zshrc` or `source ~/.bashrc`.

## Windows Setup

Run these commands in PowerShell from the repository root:

```powershell
$workspace = "$HOME\Desktop\ws"
New-Item -ItemType Directory -Force $workspace
[Environment]::SetEnvironmentVariable("RAMANUJAN_WS", $workspace, "User")
$env:RAMANUJAN_WS = $workspace

python -m pip install ast2json
mvn -f commons\pom.xml clean install -DskipTests
mvn -f rule-engine\pom.xml clean install -DskipTests
mvn -f developer-console-model\pom.xml clean install -DskipTests
mvn -f middleware\translation\pom.xml clean install -DskipTests
mvn -f developer-console\pom.xml clean install -DskipTests

.\ramanujan-native\native\buildWindows.ps1 -BuildDirectory C:\ramanujan-native-build
Copy-Item developer-console\target\developer-console-1.0-SNAPSHOT-fat.jar `
    $workspace -Force
Copy-Item C:\ramanujan-native-build\native.dll $workspace -Force
```

Install `rj` in the PowerShell profile once so it is available in every new
terminal:

```powershell
New-Item -ItemType Directory -Force (Split-Path $PROFILE)
@'
function global:rj {
    $workspace = [Environment]::GetEnvironmentVariable("RAMANUJAN_WS", "User")
    & java -Xms64m -Xmx512m `
        -jar "$workspace\developer-console-1.0-SNAPSHOT-fat.jar" @args
}
'@ | Add-Content $PROFILE
. $PROFILE
```

To build the Android worker library from Windows, install NDK
`26.1.10909125` with Android Studio and run:

```powershell
.\ramanujan-native\native\compileAndroidWindows.ps1 `
    -NdkPath "$env:LOCALAPPDATA\Android\Sdk\ndk\26.1.10909125"
```

The script copies `libnative.so` to
`androidapp\app\src\main\jniLibs\arm64-v8a`. It enables Android OpenCL by
default; pass `-DisableGpu` for a CPU-only build.

## Run on Homelab

Run all commands from the repository root.

Start the homelab server in one terminal:

```sh
rj homelab
```

The server prints its address and opens an interactive prompt:

```text
HOMELAB_READY
HOMELAB_ADDRESS http://<server-ip>:8888
```

To use an Android phone as the worker, connect the phone and computer to the
same local network. In the Android app's **Server URL** field, enter the complete
URL printed after `HOMELAB_ADDRESS`, including `http://` and port `8888`. For
example, if the server prints:

```text
HOMELAB_ADDRESS http://192.168.1.42:8888
```

enter exactly `http://192.168.1.42:8888` and tap **Start Workers**. Do not enter
`localhost`; on Android, `localhost` refers to the phone rather than the
computer running `rj homelab`.

Start a worker in another terminal:

```sh
rj worker http://localhost:8888 4
```

Then enter this command in the homelab server terminal:

```text
run ramanujan-test-codes/monte_carlo/monte_carlo_pi.py
```

Wait for `KERNEL_DONE`, then query the results in the same server terminal:

```text
var estimated_pi
var total_points_in_circle
```

`estimated_pi` should be close to `3.14159`. Because this is a randomized
simulation, the exact value changes on every run.

## Direct JAR Commands

The equivalent commands without the `rj` helper are:

```sh
java -Xms64m -Xmx512m \
  -jar developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar \
  homelab 8888
```

In another terminal, start four worker threads and point them at the server:

```sh
java -Xms64m -Xmx512m \
  -jar developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar \
  worker http://localhost:8888 4
```

Replace `localhost` with the address printed by `HOMELAB_ADDRESS` when the
worker is running on another machine.