# Contributor quick start: Monte Carlo on a computer and Android phone

[← Back to main README](../README.md)

The steps below build the developer console and native runtime from source, run
the Monte Carlo pi example locally, and then use an Android phone as a homelab
worker.

## Prerequisites

- Git, Python 3.12 or newer, and `pip`
- JDK 17 with `JAVA_HOME` set
- Maven 3.9 or newer
- CMake 3.27 or newer and a C++ compiler
- Android Studio with Android SDK 34 and NDK 26.1.10909125 for the phone build
- macOS, or Ubuntu with `libjsoncpp-dev`, `ocl-icd-opencl-dev`, and
    `opencl-headers` installed

On Ubuntu, install the native packages with:

```sh
sudo apt update
sudo apt install build-essential cmake libjsoncpp-dev ocl-icd-opencl-dev opencl-headers
```

## Clone the repository and create a workspace

The Ramanujan workspace is a separate directory for runtime binaries. It is not
the Git repository. Create both directories and set `RAMANUJAN_WS` to the
workspace's absolute path:

```sh
git clone https://github.com/Ramanujan-Computing/Ramanujan.git
cd Ramanujan

mkdir -p "$HOME/ramanujan-ws"
export RAMANUJAN_WS="$HOME/ramanujan-ws"
```

Add the `export` command to `~/.zshrc` or `~/.bashrc` to keep it across terminal
sessions.

## Build the Java modules and developer console

Install the Python translation dependency, then build the Maven modules in
dependency order:

```sh
python3 -m pip install ast2json
chmod +x projectBuilder.sh
./projectBuilder.sh
```

The final Java artifact is
`developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar`. Copy it
directly into the workspace:

```sh
cp developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar "$RAMANUJAN_WS/"
```

## Build and install the desktop native binary

`projectBuilder.sh` creates a standard native build. To configure it explicitly
with OpenCL GPU support and release optimizations, run:

```sh
cmake -S ramanujan-native/native -B ramanujan-native/native/build \
    -DGPU_ENABLED=ON -DCMAKE_BUILD_TYPE=Release
cmake --build ramanujan-native/native/build --target native_lib
```

Copy the library into the workspace beside the developer-console JAR:

```sh
# macOS
cp ramanujan-native/native/build/libnative.dylib "$RAMANUJAN_WS/"

# Ubuntu
cp ramanujan-native/native/build/libnative.so "$RAMANUJAN_WS/"
```

The workspace must now contain:

```text
ramanujan-ws/
├── developer-console-1.0-SNAPSHOT-fat.jar
└── libnative.dylib    # macOS
        libnative.so       # Ubuntu, instead of libnative.dylib
```

Install the `rj` command by adding this alias to `~/.zshrc` or `~/.bashrc`, then
reload that file:

```sh
alias rj='java -jar "$RAMANUJAN_WS/developer-console-1.0-SNAPSHOT-fat.jar"'
source ~/.zshrc   # use ~/.bashrc when running Bash
```

Run `rj` in a new terminal to verify that the command and workspace environment
are available.

Alternatively, `./install_ramanujan.sh` prompts for a workspace, downloads a
released developer-console JAR, and installs the same alias. When developing
from source, overwrite that downloaded JAR with the one built above.

## Run the Monte Carlo simulation locally

Use two terminals from the repository root.

In terminal 1, start the homelab server:

```sh
rj homelab
```

Wait for `HOMELAB_READY`. In terminal 2, start a local native worker:

```sh
rj worker http://localhost:8888 4
```

In the interactive prompt in terminal 1, submit the simulation and inspect its
result:

```text
run ramanujan-test-codes/monte_carlo/monte_carlo_pi.py
var estimated_pi
var total_points_in_circle
```

Wait for `KERNEL_DONE` before entering the `var` commands. `estimated_pi` should
be close to `3.14159`; its exact value changes on every run.

## Build the Android native binary

Android Studio installs the NDK under the Android SDK. Find the installed NDK
path in Android Studio under **Settings > Languages & Frameworks > Android SDK >
SDK Tools**, or list the versions from a terminal:

```sh
ls "$HOME/Library/Android/sdk/ndk"   # macOS default
ls "$HOME/Android/Sdk/ndk"          # Linux default
```

From the repository root, pass the selected NDK directory to
`compileAndroid.sh`:

```sh
cd ramanujan-native/native
chmod +x compileAndroid.sh
./compileAndroid.sh "$HOME/Library/Android/sdk/ndk/26.1.10909125"
cd ../..
```

On Linux, replace the argument with the corresponding path under
`$HOME/Android/Sdk/ndk`. The script builds the `arm64-v8a` release library at
`ramanujan-native/native/build/arm64-v8a/libnative.so` and automatically copies
it to `androidapp/app/src/main/jniLibs/arm64-v8a/libnative.so`, where Gradle
packages it into the APK.

## Build and run the Android app

The Android app depends on `ramanujan-device-common` from the local Maven
repository. It was installed by `projectBuilder.sh`; if only the app is being
rebuilt, refresh it with:

```sh
mvn -f commons/pom.xml clean install
mvn -f rule-engine/pom.xml clean install
mvn -f ramanujan-device-common/pom.xml clean install
```

Then:

1. Open the `androidapp` directory in Android Studio.
2. Allow Gradle sync to finish and accept any requested SDK installation.
3. Connect an arm64 Android phone with Developer options and USB debugging
     enabled, then accept the debugging prompt on the phone.
4. Select the phone as the run target and click **Run app**.

To build an APK without Android Studio, run:

```sh
cd androidapp
./gradlew assembleDebug
```

The APK is written to `androidapp/app/build/outputs/apk/debug/app-debug.apk`.

## Run Monte Carlo on the phone

Connect the computer and phone to the same local network. Start `rj homelab` on
the computer and note the non-`localhost` URL printed as `HOMELAB_ADDRESS`, for
example `http://192.168.1.42:8888`.

Open the Android app and enter the complete URL after `HOMELAB_ADDRESS` in
**Server URL**, including `http://` and port `8888`. For example, if the server
prints `HOMELAB_ADDRESS http://192.168.1.42:8888`, enter exactly
`http://192.168.1.42:8888`, then tap **Start Workers**. Do not use `localhost`
in the app because that refers to the phone itself. Allow notification
permission when prompted so the foreground worker can continue running.

Submit the same commands in the computer's homelab terminal:

```text
run ramanujan-test-codes/monte_carlo/monte_carlo_pi.py
var estimated_pi
```

Stop the desktop `rj worker` first when you want to confirm that the phone is
the device executing the simulation. If the phone cannot connect, verify that
both devices are on the same Wi-Fi network and that the computer's firewall
allows inbound TCP connections on port `8888`.

