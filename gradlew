#!/bin/sh

# Standard Gradle Wrapper Script
# Implementation for POSIX-compliant shells

APP_BASE_NAME=`basename "$0"`
APP_HOME="`pwd -P`"

# Resolve links ($0 may be a softlink)
PRG="$0"
while [ -h "$PRG" ] ; do
    ls=`ls -ld "$PRG"`
    link=`expr "$ls" : '.*-> \(.*\)$'`
    if expr "$link" : '/.*' > /dev/null; then
        PRG="$link"
    else
        PRG=`dirname "$PRG"`"/$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \"$PRG\"`" >/dev/null
APP_HOME="`pwd -P`"
cd "$SAVED" >/dev/null

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

# Determine the Java command to use
if [ -n "$JAVA_HOME" ] ; then
    if [ -x "$JAVA_HOME/jre/sh/java" ] ; then
        JAVACMD="$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="$JAVA_HOME/bin/java"
    fi
else
    JAVACMD="java"
fi

# Download gradle-wrapper.jar if not present
if [ ! -f "$CLASSPATH" ]; then
    echo "Downloading Gradle Wrapper Jar..."
    mkdir -p "$APP_HOME/gradle/wrapper"
    curl -sLo "$CLASSPATH" "https://raw.githubusercontent.com/gradle/gradle/master/gradle/wrapper/gradle-wrapper.jar" || \
    curl -sLo "$CLASSPATH" "https://repo.maven.apache.org/maven2/org/gradle/gradle-tooling-api/8.7/gradle-tooling-api-8.7.jar"
fi

exec "$JAVACMD" "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
