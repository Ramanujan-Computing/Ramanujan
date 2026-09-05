# Build and usage Strategy:

[← Back to main README](../README.md)
## Build:
Use `mvn clean install`. Following is the dependency hierarchy:

> **Tip:** [`projectBuilder.sh`](../projectBuilder.sh) automates the whole build below — it runs `mvn clean install`
> for every module in dependency order, builds the native project with CMake, then prompts for a `RAMANUJAN_WS`
> workspace directory and copies the `developer-console` fat JAR and the native library (`libnative.dylib`/`libnative.so`)
> into it, and finally adds the `rj` alias to your shell profile. Run it with `./projectBuilder.sh` from the repo root.
### Lower level dependencies:
1. commons
2. rule-engine
3. ramanujan-device-common
4. developer-console-model
5. monitoring-utils
6. db-layer

### Second level dependencies:
1. kafka-manager
2. orchestrator

### Third level dependencies:
1. middlware

### Fourth level dependencies:
1. developer-console

### Ramanujan-native:
```
cd ramanujan-native/native
mkdir build
cd build
cmake ..
cmake --build .
```

#### Ramanujan-native with GPU support (OpenCL):
```
cd ramanujan-native/native
mkdir build-gpu
cd build-gpu
cmake -DGPU_ENABLED=ON -DCMAKE_BUILD_TYPE=Release ..
cmake --build .
```
Passing `-DGPU_ENABLED=ON` sets the `GPU_ENABLED` preprocessor macro, links OpenCL, and activates
OpenCL kernel dispatch for `_GPU`-suffixed functions. Standard builds (`-DGPU_ENABLED=OFF`, the
default) compile no OpenCL code and have no dependency on any OpenCL runtime.
### Docker build:
Dockerfile is provided to containerize all the necessary services.

### Required APIs:
#### Middleware server:
1. PUT /orchestrator?ip=<orchestrator_ip>&port=<orchestrator_port>
2. PUT /kafka?ip=<kafka_manager_ip>&port=<kafka_manager_port>

#### Kafka Manager server:
1. PUT /middleware?ip=<middleware_ip>&port=<middleware_port>

#### For using experimental `prayog` device server on the network:
1. PUT /start?devices=<number_of_devices_to_emulate>

## Important configs:
### middleware:
1. orchestrator.host
2. orchestrator.port
3. kafka.host
4. kafka.port
5. db.type : "GCP", "IN_MEM"
6. storage.type : "GCP", "LOCAL"
7. monitoring.type : "GCP", "LOCAL"

### Orchestrator:
1. db.type : "GCP", "IN_MEM"
2. storage.type : "GCP", "LOCAL"
3. monitoring.type : "GCP", "LOCAL"

## Developer Console:
For executing code file:
```java -jar <developer-console-path>/target/developer-console-1.0-SNAPSHOT-fat.jar execute <path-to-code-file>```

## Python Dependencies for Translation Module

The translation module (middleware-translation) currently requires the following Python dependencies to convert Python code to Ramanujan intermediate code:

- **Python 3.x**: Required for AST generation [In particular >= 3.12]
- **ast2json** _(may be removed in future versions)_: BSD-licensed library for converting Python AST to JSON format
  - Install: `pip install ast2json`
  - Repository: https://github.com/YoloSwagTeam/ast2json
  - License: BSD-3-Clause

For more information about third-party licenses, see [THIRD_PARTY_LICENSES.md](../THIRD_PARTY_LICENSES.md).

## Installing Ramanujan Console for executing on-current-device (Ubuntu & macOS)

To set up the Ramanujan developer console and required dependencies, run the provided installer script:

```sh
# Make the installer executable
chmod +x install_ramanujan.sh

# Run the installer
./install_ramanujan.sh
```

- The script will prompt you for a workspace path and set the `RAMANUJAN_WS` environment variable.
- It will download the latest developer console JAR to your workspace.
- It will add an alias `rj` to your shell profile for easy usage.

**After installation, restart your terminal or run:**

```sh
source ~/.zshrc   # or ~/.bashrc, ~/.bash_profile, depending on your shell
```

You can now run the developer console with:

```sh
rj <path-to-code-file>
```

