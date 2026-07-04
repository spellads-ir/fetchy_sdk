#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_NAME="fetchy-sdk"
JAVA_21_HOME="/usr/lib/jvm/java-21-openjdk"

echo "==> Fetchy SDK AAR build"

if [ ! -d "$JAVA_21_HOME" ]; then
  echo "ERROR: Java 21 not found at: $JAVA_21_HOME"
  echo "Install it with:"
  echo "  sudo pacman -S jdk21-openjdk"
  exit 1
fi

export JAVA_HOME="$JAVA_21_HOME"
export PATH="$JAVA_HOME/bin:$PATH"

echo "==> Using Java:"
java -version

cd "$PROJECT_ROOT"

echo "==> Cleaning previous build..."
sh ./gradlew ":$MODULE_NAME:clean"

echo "==> Building release AAR..."
sh ./gradlew ":$MODULE_NAME:assembleRelease"

AAR_PATH="$PROJECT_ROOT/$MODULE_NAME/build/outputs/aar/$MODULE_NAME-release.aar"

if [ ! -f "$AAR_PATH" ]; then
  echo "ERROR: AAR was not created at:"
  echo "  $AAR_PATH"
  exit 1
fi

echo ""
echo "BUILD SUCCESSFUL"
echo "AAR created at:"
echo "$AAR_PATH"
