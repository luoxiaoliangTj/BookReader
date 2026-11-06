#!/usr/bin/env bash
set -e
exec java -jar "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" "$@"