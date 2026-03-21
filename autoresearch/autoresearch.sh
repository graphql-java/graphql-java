#!/usr/bin/env bash
# Autoresearch loop driver for graphql-java ENF optimization.
#
# This script runs an autonomous optimization loop using Claude Code (Sonnet)
# to iteratively improve ENF performance.
#
# Usage:
#   ./autoresearch/autoresearch.sh [max_iterations]
#
# Prerequisites:
#   - Claude Code CLI installed and authenticated
#   - Java toolchain (JDK 25) available for builds
#
# The loop:
#   1. Get baseline benchmark score
#   2. Ask Claude to make ONE optimization
#   3. Run tests + benchmark
#   4. Keep if improved, revert if not
#   5. Repeat

set -euo pipefail

MAX_ITERATIONS="${1:-50}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_FILE="$SCRIPT_DIR/results.tsv"
BEST_SCORE_FILE="$SCRIPT_DIR/.best_score"

cd "$PROJECT_DIR"

# Initialize log
if [ ! -f "$LOG_FILE" ]; then
    echo -e "iteration\tcommit\tscore\tdelta\tstatus\tdescription" > "$LOG_FILE"
fi

# Get baseline score
echo "=== Getting baseline score ==="
BASELINE=$(bash "$SCRIPT_DIR/run_benchmark.sh")
if [ "$BASELINE" = "FAILED" ]; then
    echo "ERROR: Baseline benchmark failed. Fix issues before starting autoresearch."
    exit 1
fi
echo "Baseline: $BASELINE ops/s"
echo "$BASELINE" > "$BEST_SCORE_FILE"

BEST_SCORE="$BASELINE"
COMMIT_BEFORE=$(git rev-parse HEAD)

for i in $(seq 1 "$MAX_ITERATIONS"); do
    echo ""
    echo "========================================"
    echo "=== Iteration $i / $MAX_ITERATIONS ==="
    echo "=== Best score: $BEST_SCORE ops/s ==="
    echo "========================================"

    # Save current state
    COMMIT_BEFORE=$(git rev-parse HEAD)

    # Ask Claude (Sonnet) to make ONE optimization
    # Using --print to run non-interactively
    claude --model sonnet -p "$(cat <<EOF
You are running iteration $i of an autoresearch optimization loop for graphql-java.

Read autoresearch/program.md for full context and strategy.

Current best benchmark score: $BEST_SCORE ops/s (baseline was: $BASELINE ops/s)

Previous optimization log:
$(tail -10 "$LOG_FILE" 2>/dev/null || echo "No previous iterations")

YOUR TASK: Make exactly ONE focused optimization to the ENF code.
- Pick the most promising unused strategy from program.md
- Make a minimal, targeted change
- Do NOT run tests or benchmarks (the harness does that)
- Describe what you changed and why in a single line

IMPORTANT: Only modify files under src/main/java/graphql/normalized/ or the utility
files mentioned in program.md. Make the change now.
EOF
)"

    # Check if anything changed
    if git diff --quiet src/main/java/; then
        echo "No changes made in iteration $i, skipping"
        echo -e "$i\t-\t-\t-\tskipped\tno changes" >> "$LOG_FILE"
        continue
    fi

    # Run tests
    echo "--- Running tests ---"
    if ! ./gradlew test -q 2>&1 | tail -5; then
        echo "Tests FAILED — reverting"
        git checkout -- src/
        echo -e "$i\t-\t-\t-\treverted\ttests failed" >> "$LOG_FILE"
        continue
    fi

    # Run benchmark
    echo "--- Running benchmark ---"
    SCORE=$(bash "$SCRIPT_DIR/run_benchmark.sh")
    if [ "$SCORE" = "FAILED" ]; then
        echo "Benchmark FAILED — reverting"
        git checkout -- src/
        echo -e "$i\t-\t-\t-\treverted\tbenchmark failed" >> "$LOG_FILE"
        continue
    fi

    # Compare (using awk for floating point)
    IMPROVED=$(echo "$SCORE $BEST_SCORE" | awk '{print ($1 > $2) ? "yes" : "no"}')
    DELTA=$(echo "$SCORE $BEST_SCORE" | awk '{printf "%.3f", $1 - $2}')

    if [ "$IMPROVED" = "yes" ]; then
        echo "IMPROVED! $BEST_SCORE -> $SCORE ops/s (+$DELTA)"
        BEST_SCORE="$SCORE"
        echo "$BEST_SCORE" > "$BEST_SCORE_FILE"

        # Get a description of the change
        DESCRIPTION=$(git diff --stat src/main/java/ | head -1)

        # Commit the improvement
        git add src/main/java/
        git commit -m "autoresearch: iteration $i — $DESCRIPTION [+$DELTA ops/s]"

        COMMIT=$(git rev-parse --short HEAD)
        echo -e "$i\t$COMMIT\t$SCORE\t+$DELTA\tkept\t$DESCRIPTION" >> "$LOG_FILE"
    else
        echo "No improvement: $SCORE vs $BEST_SCORE ops/s ($DELTA) — reverting"
        git checkout -- src/
        echo -e "$i\t-\t$SCORE\t$DELTA\treverted\tno improvement" >> "$LOG_FILE"
    fi
done

echo ""
echo "========================================"
echo "=== Autoresearch complete ==="
echo "=== Baseline: $BASELINE ops/s ==="
echo "=== Final best: $BEST_SCORE ops/s ==="
echo "=== Total improvement: $(echo "$BEST_SCORE $BASELINE" | awk '{printf "%.3f", $1 - $2}') ops/s ==="
echo "========================================"
echo ""
echo "Results log: $LOG_FILE"
