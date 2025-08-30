# Ramanujan Distributed Computing Platform

Always reference these instructions first and fallback to search or bash commands only when you encounter unexpected information that does not match the info here.

## Working Effectively

### System Requirements and Setup
- Install full OpenJDK (not just runtime): `sudo apt update && sudo apt install -y openjdk-17-jdk`
- Set environment variable: `export JDK_HOME=/usr/lib/jvm/java-17-openjdk-amd64`
- Maven 3.9.11+ and CMake 3.27+ are required
- Node.js 20+ required for webapp components

### Core Java Modules Build Process
Build Java modules in this EXACT order using Maven:

```bash
# NEVER CANCEL these builds. Each takes 3-30 seconds. Set timeouts to 120+ seconds.
cd commons && mvn clean install              # ~24 seconds
cd ../rule-engine && mvn clean install       # ~6 seconds  
cd ../ramanujan-device-common && mvn clean install  # ~5 seconds
cd ../developer-console-model && mvn clean install  # ~3 seconds
cd ../monitoring-utils2 && mvn clean install        # ~14 seconds
cd ../db-layer && mvn clean install                 # ~17 seconds
```

**KNOWN BUILD ISSUES:**
- `kafka-manager` and `orchestrator` rest modules FAIL due to maven-shade-plugin configuration errors
- `middleware` and `developer-console` have circular dependencies
- These are KNOWN ISSUES in the codebase - do not attempt to fix them

### Native Component Build
The native component (C++) builds successfully with correct environment:

```bash
cd ramanujan-native/native
export JDK_HOME=/usr/lib/jvm/java-17-openjdk-amd64
mkdir -p build && cd build
cmake ..
cmake --build .  # ~35 seconds - NEVER CANCEL. Set timeout to 120+ seconds.
```

**CRITICAL:** The build REQUIRES JDK_HOME (not JAVA_HOME) and full OpenJDK with headers.

### Developer Console Installation
Use the provided installer for end-user installation:

```bash
chmod +x install_ramanujan.sh
./install_ramanujan.sh
# Follow prompts to set workspace path
# JAR download may fail if release doesn't exist yet
```

After installation: `source ~/.zshrc` or `source ~/.bashrc`
Usage: `rj <path-to-ramanujan-file>`

### Webapp Component
**WARNING:** The webapp has Node.js backend code but NO package.json files. The build.sh script expects package.json but they don't exist. This appears to be incomplete or requires additional setup not documented.

- Backend is Express.js with MySQL dependencies
- Frontend appears to be React-based but lacks package management
- Docker Compose configuration exists for production deployment
- **DO NOT attempt to npm install** - there are no package.json files

## Validation Workflows

### Basic Build Validation
Always run these commands to verify your changes don't break the working components:

```bash
# Test core modules (should succeed)
cd commons && mvn clean install
cd ../rule-engine && mvn clean install
cd ../ramanujan-device-common && mvn clean install

# Test native build (should succeed)
cd ../ramanujan-native/native
export JDK_HOME=/usr/lib/jvm/java-17-openjdk-amd64
mkdir -p build && cd build && cmake .. && cmake --build .
```

### Sample Ramanujan Code Testing
Create test files to validate functionality:

```ramanujan
var x:integer;
var y:integer;
var result:integer;

x = 10;
y = 20;
result = x + y;
```

## Build Time Expectations
**NEVER CANCEL builds or long-running commands.** Set appropriate timeouts:

- Commons module: ~24 seconds
- Rule-engine: ~6 seconds  
- Ramanujan-device-common: ~5 seconds
- Developer-console-model: ~3 seconds
- Monitoring-utils2: ~14 seconds
- Database layer: ~17 seconds
- Native component: ~35 seconds
- **Total successful build time: ~104 seconds**

## Repository Structure

### Key Java Modules
```
commons/           # Base utilities and common code
rule-engine/       # Core language processing
ramanujan-device-common/  # Device communication protocols
developer-console-model/  # Data models for dev console
monitoring-utils2/        # Monitoring and metrics utilities
db-layer/         # Database abstraction layer
```

### Platform Services (Build Issues)
```
kafka-manager/    # Message queue management - REST MODULE FAILS
orchestrator/     # Task distribution - REST MODULE FAILS  
middleware/       # Code translation service - DEPENDENCY ISSUES
developer-console/ # CLI tool - DEPENDS ON MIDDLEWARE
```

### Other Components
```
ramanujan-native/ # C++ native processing library - BUILDS OK
webapp/          # React/Express web interface - INCOMPLETE
androidapp/      # Android client application
prayog/          # Experimental device emulator
```

## Common Issues and Workarounds

### Maven Shade Plugin Error
**Error:** `Unable to parse configuration of mojo maven-shade-plugin:2.3:shade for parameter Main-Class`
**Affected:** kafka-manager/rest, orchestrator/rest  
**Workaround:** This is a known configuration issue in the POM files. Skip these modules.

### Missing JNI Headers
**Error:** `fatal error: jni.h: No such file or directory`
**Solution:** Install full JDK: `sudo apt install -y openjdk-17-jdk`
**Critical:** Must set `JDK_HOME=/usr/lib/jvm/java-17-openjdk-amd64`

### Webapp Package Management
**Issue:** Node.js backend exists but no package.json files
**Status:** This appears to be incomplete in the repository
**Workaround:** Use Docker Compose for webapp deployment

### Developer Console Dependencies  
**Error:** `Could not find artifact in.ramanujan.middleware:translation:jar:1.0-SNAPSHOT`
**Cause:** Circular dependency between middleware and developer-console
**Status:** Known issue - these components cannot be built in the current state

## Development Workflow

1. **Always build core modules first** in the specified order
2. **Test native component** if making changes to language processing
3. **Use sample Ramanujan files** to validate changes
4. **Never attempt to build kafka-manager or orchestrator REST modules**
5. **Use Docker for webapp** rather than trying to fix missing package.json files

## External Dependencies
- Remote Ramanujan API: `https://server.ramanujan.dev` (may not be available in development)
- Google Cloud Platform services (for storage, monitoring when configured)
- MySQL database (for webapp)

## Testing and Validation
After making changes:
1. Build core Java modules to ensure no regressions
2. Build native component if language changes were made
3. Create simple Ramanujan test files and verify they parse correctly
4. Use the components that DO work rather than trying to fix the broken ones

The platform is a research project and some components have known issues that should be documented rather than fixed unless specifically required.