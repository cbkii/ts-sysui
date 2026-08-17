#!/bin/sh
# Minimal Gradle wrapper launcher. The wrapper JAR and distribution checksum are committed.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd) || exit 1
JAR="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ ! -r "$JAR" ]; then
    echo "ERROR: missing $JAR" >&2
    exit 1
fi
if [ -n "${JAVA_HOME:-}" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD=java
fi
if ! command -v "$JAVACMD" >/dev/null 2>&1 && [ ! -x "$JAVACMD" ]; then
    echo "ERROR: Java is not available; set JAVA_HOME or install a JDK." >&2
    exit 1
fi
exec "$JAVACMD" -Dorg.gradle.appname=gradlew -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
