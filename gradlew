#!/data/data/com/termux/files/usr/bin/bash
set -e
export GRADLE_USER_HOME=/data/data/com.termux/files/.gradle
export ANDROID_HOME=/data/data/com.termux/files/home/android-sdk
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
exec "$JAVA_HOME/bin/java" -jar "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" "$@"
