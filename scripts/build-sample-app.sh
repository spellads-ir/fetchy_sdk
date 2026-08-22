#!/usr/bin/env bash
set -euo pipefail
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk}"
cd "$(dirname "$0")/.."
DAEMON_PROPS="gradle/gradle-daemon-jvm.properties"
if [[ -f "$DAEMON_PROPS" ]]; then
  mv "$DAEMON_PROPS" "${DAEMON_PROPS}.bak"
  trap 'mv "${DAEMON_PROPS}.bak" "$DAEMON_PROPS"' EXIT
fi
sh ./gradlew :sample-app:assembleDebug --no-daemon -Dorg.gradle.java.home="$JAVA_HOME" "$@"
