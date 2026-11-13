#!/usr/bin/env bash

BRANCH=$(git rev-parse --abbrev-ref HEAD)
JAR="build/libs/graphql-java-0.0.0-$BRANCH-SNAPSHOT-jmh.jar"
echo "build and then running jmh for $JAR"

./gradlew clean jmhJar

java -jar "$JAR" "$@"