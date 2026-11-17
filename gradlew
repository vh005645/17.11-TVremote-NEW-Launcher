#!/bin/sh
# Simplified Gradle wrapper for RemoteTV
# Uses system gradle if available, otherwise downloads Gradle 8.7
set -e
GRADLE_VERSION="8.7"
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
GRADLE_HOME="$HOME/.gradle/wrapper/dists/gradle-${GRADLE_VERSION}-all"
GRADLE_BIN="$GRADLE_HOME/gradle-${GRADLE_VERSION}/bin/gradle"
if [ ! -f "$GRADLE_BIN" ]; then
  echo "Downloading Gradle $GRADLE_VERSION..."
  mkdir -p "$GRADLE_HOME"
  cd "$GRADLE_HOME"
  if command -v curl >/dev/null 2>&1; then
    curl -L -o gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-all.zip"
  else
    wget -O gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-all.zip"
  fi
  unzip -o -q gradle.zip
  rm gradle.zip
fi
exec "$GRADLE_BIN" "$@"
