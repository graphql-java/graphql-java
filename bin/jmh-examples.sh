#!/usr/bin/env bash

# Example script demonstrating common JMH profiling scenarios
# This script is for demonstration purposes only

set -e

echo "==================================================================="
echo "JMH Profiling Examples for graphql-java"
echo "==================================================================="
echo ""

# Example 1: Basic allocation profiling with flamegraph
echo "Example 1: Basic allocation profiling with flamegraph"
echo "-------------------------------------------------------------------"
echo "Command: ./bin/jmh.sh 'GetterAccessBenchmark' -wi 2 -i 2 -f 1 -prof 'async:event=alloc;output=flamegraph'"
echo ""
echo "This will:"
echo "  - Run all GetterAccessBenchmark tests"
echo "  - 2 warmup iterations, 2 measurement iterations"
echo "  - 1 fork (JVM process)"
echo "  - Profile allocations and generate flamegraph HTML files"
echo ""
echo "Output will be in: benchmark.GetterAccessBenchmark.*-Throughput/"
echo ""
read -p "Press Enter to run this example (or Ctrl+C to skip)..."
./bin/jmh.sh "GetterAccessBenchmark" -wi 2 -i 2 -f 1 -prof "async:event=alloc;output=flamegraph"
echo ""
echo "==================================================================="
echo ""

# Example 2: Profile a specific benchmark with more detail
echo "Example 2: Profile a specific method with text output"
echo "-------------------------------------------------------------------"
echo "Command: ./bin/jmh.sh 'ComplexQueryBenchmark.benchMarkSimpleQueriesThroughput' -wi 1 -i 1 -f 1 -prof 'async:event=alloc'"
echo ""
echo "This will:"
echo "  - Run only the benchMarkSimpleQueriesThroughput test"
echo "  - 1 warmup iteration, 1 measurement iteration"
echo "  - Show allocation summary in text format"
echo ""
read -p "Press Enter to run this example (or Ctrl+C to skip)..."
./bin/jmh.sh "ComplexQueryBenchmark.benchMarkSimpleQueriesThroughput" -wi 1 -i 1 -f 1 -prof "async:event=alloc"
echo ""
echo "==================================================================="
echo ""

# Example 3: List available benchmarks
echo "Example 3: List all available benchmarks"
echo "-------------------------------------------------------------------"
echo "Command: ./bin/jmh.sh -l"
echo ""
read -p "Press Enter to list benchmarks (or Ctrl+C to skip)..."
./bin/jmh.sh -l
echo ""
echo "==================================================================="
echo ""

echo "Examples complete!"
echo ""
echo "For more information, see PERFORMANCE-PROFILING.md"
echo "==================================================================="
