#!/usr/bin/env bash
# Runs the ComplexQuery throughput benchmark and extracts the score.
# Usage: ./autoresearch-execution-complex/run_benchmark.sh
# Output: prints the benchmark score (ops/sec) to stdout, or "FAILED" on error.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

cd "$PROJECT_DIR"

echo "=== Running ComplexQuery throughput benchmark ===" >&2
BENCHMARK_OUTPUT=$(./gradlew jmh \
    -PjmhInclude="performance.ComplexQueryPerformance.benchMarkSimpleQueriesThroughput" \
    -PjmhFork=1 \
    -PjmhIterations=2 \
    -PjmhWarmupIterations=2 \
    2>&1)

# Extract score from JMH output — take the first (howManyItems=5) result for consistency
# ComplexQueryPerformance.benchMarkSimpleQueriesThroughput    5  thrpt    2  XX.XXX ± Y.YYY  ops/s
SCORE=$(echo "$BENCHMARK_OUTPUT" | grep -E "benchMarkSimpleQueriesThroughput\s+" | head -1 | awk '{print $(NF-3)}')

if [ -z "$SCORE" ]; then
    echo "FAILED: could not extract benchmark score" >&2
    echo "Last 20 lines of output:" >&2
    echo "$BENCHMARK_OUTPUT" | tail -20 >&2
    echo "FAILED"
    exit 1
fi

echo "Score: $SCORE ops/s" >&2
echo "$SCORE"
