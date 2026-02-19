#!/usr/bin/env bash

# Find the JMH jar file
JAR=$(ls build/libs/*-jmh.jar 2>/dev/null | head -1)

if [ ! -f "$JAR" ]; then
    echo "JMH jar not found. Building..."
    ./gradlew clean jmhJar
    JAR=$(ls build/libs/*-jmh.jar 2>/dev/null | head -1)
fi

if [ ! -f "$JAR" ]; then
    echo "Error: Could not find or build JMH jar"
    exit 1
fi

echo "Using JMH jar: $JAR"

# Extract async-profiler native library if needed
ASYNC_PROFILER_DIR="build/async-profiler"
if [[ "$*" == *"async"* ]]; then
    echo "Extracting async-profiler native library..."
    mkdir -p "$ASYNC_PROFILER_DIR"
    cd "$ASYNC_PROFILER_DIR" || exit 1
    
    # Extract async-profiler jar from JMH jar
    unzip -o -q "../../$JAR" async-profiler-3.0.jar 2>/dev/null
    
    # Extract native library based on OS
    if [[ -f async-profiler-3.0.jar ]]; then
        if [[ "$OSTYPE" == "linux-gnu"* ]]; then
            unzip -o -q async-profiler-3.0.jar "linux-x64/libasyncProfiler.so" 2>/dev/null
            export LD_LIBRARY_PATH="$PWD/linux-x64:$LD_LIBRARY_PATH"
            echo "Set LD_LIBRARY_PATH to include $PWD/linux-x64"
        elif [[ "$OSTYPE" == "darwin"* ]]; then
            unzip -o -q async-profiler-3.0.jar "macos/libasyncProfiler.so" 2>/dev/null
            export DYLD_LIBRARY_PATH="$PWD/macos:$DYLD_LIBRARY_PATH"
            echo "Set DYLD_LIBRARY_PATH to include $PWD/macos"
        fi
    fi
    cd - > /dev/null || exit 1
fi

java -jar "$JAR" "$@"