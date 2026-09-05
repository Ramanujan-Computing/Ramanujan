# Build dependencies in the order given in README.md, then install the
# resulting developer-console JAR and native library into RAMANUJAN_WS.

REPO_ROOT="$(cd "$(dirname "$0")" && pwd)"

# Ask for the Ramanujan workspace directory (where runtime binaries are installed)
if [ -z "$RAMANUJAN_WS" ]; then
    echo "Enter the absolute path to your Ramanujan workspace (e.g., /home/user/ramanujan-ws or /Users/user/ramanujan-ws):"
    read -r RAMANUJAN_WS
fi
mkdir -p "$RAMANUJAN_WS"

cd "$REPO_ROOT"/commons
mvn clean install

cd ../rule-engine
mvn clean install

cd ../ramanujan-device-common
mvn clean install

cd ../developer-console-model
mvn clean install

cd ../monitoring-utils2
mvn clean install

cd ../db-layer
mvn clean install

cd ../kafka-manager
mvn clean install

cd ../orchestrator
mvn clean install

cd ../middleware
mvn clean install

cd ../developer-console
mvn clean install

cd ../ramanujan-native/native
[ ! -d "build" ] && mkdir build
cd build
cmake ..
cmake --build .

# Install the freshly built binaries into the workspace
cd "$REPO_ROOT"
cp developer-console/target/developer-console-1.0-SNAPSHOT-fat.jar "$RAMANUJAN_WS/"

OS_NAME="$(uname)"
if [ "$OS_NAME" = "Darwin" ]; then
    NATIVE_LIB="libnative.dylib"
elif [ "$OS_NAME" = "Linux" ]; then
    NATIVE_LIB="libnative.so"
else
    echo "[ERROR] Unsupported OS for native library copy: $OS_NAME"
    exit 1
fi
cp "ramanujan-native/native/build/$NATIVE_LIB" "$RAMANUJAN_WS/"
echo "[INFO] Installed developer-console JAR and $NATIVE_LIB into $RAMANUJAN_WS"

# Persist RAMANUJAN_WS and the rj alias in the shell profile
PROFILE="$HOME/.zshrc"
[ "$SHELL" = "/bin/zsh" ] || [ -f "$HOME/.zshrc" ] || PROFILE="$HOME/.bashrc"

grep -q 'export RAMANUJAN_WS=' "$PROFILE" 2>/dev/null && sed -i.bak '/export RAMANUJAN_WS=/d' "$PROFILE"
echo "export RAMANUJAN_WS=\"$RAMANUJAN_WS\"" >> "$PROFILE"

grep -q 'alias rj=' "$PROFILE" 2>/dev/null && sed -i.bak '/alias rj=/d' "$PROFILE"
echo 'alias rj="java -jar \"$RAMANUJAN_WS/developer-console-1.0-SNAPSHOT-fat.jar\""' >> "$PROFILE"

echo "[SUCCESS] Build complete. RAMANUJAN_WS and the 'rj' alias were added to $PROFILE."
echo "Run: source $PROFILE   (or restart your terminal) to start using 'rj'."