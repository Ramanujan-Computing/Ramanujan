#!/usr/bin/env bash

# Ramanujan Installer for Ubuntu and macOS only
set -e

# Detect OS
ios="$(uname)"

# Only allow macOS (Darwin) and Ubuntu (Linux)
if [ "$ios" = "Darwin" ]; then
    OS_OK=1
elif [ "$ios" = "Linux" ]; then
    if grep -qi 'ubuntu' /etc/os-release 2>/dev/null; then
        OS_OK=1
    else
        echo "[ERROR] Only Ubuntu Linux is supported. Exiting."
        exit 1
    fi
else
    echo "[ERROR] Only Ubuntu Linux and macOS are supported. Exiting."
    exit 1
fi

# Ask user for workspace path
echo "Enter the absolute path to your Ramanujan workspace (e.g., /home/user/ramanujan or /Users/user/ramanujan):"
read -r RAMANUJAN_WS

# Set environment variable in the appropriate shell profile
PROFILE=""
if [ "$SHELL" = "/bin/zsh" ] || [ -f "$HOME/.zshrc" ]; then
    PROFILE="$HOME/.zshrc"
elif [ -f "$HOME/.bash_profile" ]; then
    PROFILE="$HOME/.bash_profile"
elif [ -f "$HOME/.bashrc" ]; then
    PROFILE="$HOME/.bashrc"
else
    PROFILE="$HOME/.profile"
fi

grep -q 'export RAMANUJAN_WS=' "$PROFILE" && sed -i.bak '/export RAMANUJAN_WS=/d' "$PROFILE"
echo "export RAMANUJAN_WS=\"$RAMANUJAN_WS\"" >> "$PROFILE"
echo "[INFO] RAMANUJAN_WS set in $PROFILE"

# Download developer-console JAR to RAMANUJAN_WS
JAR_PATH="$RAMANUJAN_WS/developer-console-1.0-SNAPSHOT-fat.jar"
JAR_URL="https://github.com/Ramanujan-Computing/Ramanujan/releases/download/standalone/developer-console-1.0-SNAPSHOT-fat.jar"
echo "[INFO] Downloading developer-console JAR to $JAR_PATH ..."
curl -L -o "$JAR_PATH" "$JAR_URL"
echo "[INFO] JAR downloaded."

# Add alias to shell profile
ALIAS_LINE='alias rj="java -jar $RAMANUJAN_WS/developer-console-1.0-SNAPSHOT-fat.jar"'
grep -q 'alias rj=' "$PROFILE" && sed -i.bak '/alias rj=/d' "$PROFILE"
echo "$ALIAS_LINE" >> "$PROFILE"
echo "[INFO] Alias 'rj' added to $PROFILE"

# Check for libjsoncpp
check_jsoncpp() {
    if command -v pkg-config >/dev/null 2>&1; then
        pkg-config --exists jsoncpp && return 0
    fi
    if [ "$ios" = "Darwin" ]; then
        brew list jsoncpp >/dev/null 2>&1 && return 0
    else
        ldconfig -p | grep -q jsoncpp && return 0
        dpkg -l | grep -q jsoncpp && return 0
    fi
    return 1
}

if check_jsoncpp; then
    echo "[INFO] libjsoncpp is already installed."
else
    echo "[INFO] libjsoncpp not found. Installing..."
    if [ "$ios" = "Darwin" ]; then
        if ! command -v brew >/dev/null 2>&1; then
            echo "[ERROR] Homebrew not found. Please install Homebrew first: https://brew.sh/"
            exit 1
        fi
        brew install jsoncpp
    else
        sudo apt-get update && sudo apt-get install -y libjsoncpp-dev
    fi
    echo "[INFO] libjsoncpp installed."
fi

echo "[SUCCESS] Ramanujan installer completed. Please restart your terminal or run: source $PROFILE"
