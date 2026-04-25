#!/usr/bin/env bash
# Autoresearch loop driver for graphql-java execution engine optimization.
#
# Usage:
#   ./autoresearch-execution-large/autoresearch.sh [max_iterations]
#
# Default: 200 iterations (designed for overnight runs)
#
# Safety:
#   The agent runs with --permission-mode plan and explicit --allowedTools.
#   It can read files, edit source code, and run gradle for profiling.
#   Tests, benchmarks, git commits, and reverts are handled by the outer harness.

set -euo pipefail

MAX_ITERATIONS="${1:-200}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
LOG_FILE="$SCRIPT_DIR/results.tsv"
BEST_SCORE_FILE="$SCRIPT_DIR/.best_score"
TEST_FILTER='--tests "graphql.execution.*" --tests "graphql.GraphQLTest"'

cd "$PROJECT_DIR"

# Verify claude CLI is available
if ! command -v claude &>/dev/null; then
    echo "ERROR: 'claude' CLI not found on PATH. Install Claude Code first."
    exit 1
fi

# Initialize log
if [ ! -f "$LOG_FILE" ]; then
    printf "iteration\tcommit\tscore\tdelta\tstatus\tdescription\n" > "$LOG_FILE"
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

for i in $(seq 1 "$MAX_ITERATIONS"); do
    echo ""
    echo "========================================"
    echo "=== Iteration $i / $MAX_ITERATIONS ==="
    echo "=== Best score: $BEST_SCORE ops/s ==="
    echo "========================================"

    # Build the prompt for this iteration
    RECENT_LOG=$(tail -10 "$LOG_FILE" 2>/dev/null || echo "No previous iterations")

    PROMPT="You are running iteration $i of an autoresearch optimization loop for graphql-java.

Read autoresearch-execution-large/program.md for full context and strategy.

Current best benchmark score: $BEST_SCORE ops/s (baseline was: $BASELINE ops/s)

Previous optimization log (last 10 entries):
$RECENT_LOG

YOUR TASK: Make exactly ONE focused optimization to the execution engine code.
- Read the code files first. If this is iteration 1 or you haven't profiled yet, run the
  benchmark with async-profiler first to identify hotspots.
- Pick the most promising strategy from program.md that has NOT already been tried (check the log above)
- Make a minimal, targeted change to ONE or TWO files
- Do NOT run tests or benchmarks — the outer harness handles that
- Do NOT commit — the outer harness handles that
- After editing, output a single-line summary of what you changed and why

SCOPE: Only modify files under src/main/java/graphql/execution/, src/main/java/graphql/GraphQL.java,
or the utility files listed in program.md (ImmutableKit.java, FpKit.java).

Make the change now."

    # Allowed tools: read-only exploration + code edits + safe bash commands
    # The agent can profile (gradlew jmh), inspect files, and edit source code.
    # Tests, benchmarks, git commits, and reverts are handled by this outer harness.
    ALLOWED_TOOLS='Read,Glob,Grep,Edit,Write,Bash(./gradlew:*),Bash(cat:*),Bash(wc:*),Bash(head:*),Bash(tail:*),Bash(find:*),Bash(ls:*),Bash(grep:*),Bash(git diff:*),Bash(git status:*),Bash(git log:*),Bash(git show:*),Bash(git checkout:*)'

    echo "--- Asking Claude to make an optimization ---"
    CLAUDE_OUTPUT=$(claude \
        --model sonnet \
        --permission-mode plan \
        --allowedTools "$ALLOWED_TOOLS" \
        --max-turns 25 \
        --verbose \
        -p "$PROMPT" \
        2>&1) || true

    echo "$CLAUDE_OUTPUT" | tail -5

    # Check if anything changed
    if git diff --quiet src/main/java/; then
        echo "No source changes in iteration $i, skipping"
        printf "%s\t-\t-\t-\tskipped\tno changes\n" "$i" >> "$LOG_FILE"
        continue
    fi

    # Show what changed
    echo "--- Changes made ---"
    git diff --stat src/main/java/

    # Run targeted tests locally
    echo "--- Running tests ---"
    if ! ./gradlew test --tests "graphql.execution.*" --tests "graphql.GraphQLTest" -q 2>&1 | tail -10; then
        echo "Tests FAILED — reverting changes"
        git checkout -- src/
        printf "%s\t-\t-\t-\treverted\ttests failed\n" "$i" >> "$LOG_FILE"
        continue
    fi

    # Run benchmark
    echo "--- Running benchmark ---"
    SCORE=$(bash "$SCRIPT_DIR/run_benchmark.sh")
    if [ "$SCORE" = "FAILED" ]; then
        echo "Benchmark FAILED — reverting changes"
        git checkout -- src/
        printf "%s\t-\t-\t-\treverted\tbenchmark failed\n" "$i" >> "$LOG_FILE"
        continue
    fi

    # Compare (using awk for floating point)
    IMPROVED=$(echo "$SCORE $BEST_SCORE" | awk '{print ($1 > $2) ? "yes" : "no"}')
    DELTA=$(echo "$SCORE $BEST_SCORE" | awk '{printf "%.3f", $1 - $2}')

    if [ "$IMPROVED" = "yes" ]; then
        echo ""
        echo "*** IMPROVED! $BEST_SCORE -> $SCORE ops/s (+$DELTA) ***"
        echo ""
        BEST_SCORE="$SCORE"
        echo "$BEST_SCORE" > "$BEST_SCORE_FILE"

        DESCRIPTION=$(git diff --stat src/main/java/ | tail -1 | xargs)

        git add src/main/java/
        git commit -m "autoresearch: iteration $i [+$DELTA ops/s]

$(git diff --cached --stat | head -5)"

        COMMIT=$(git rev-parse --short HEAD)
        printf "%s\t%s\t%s\t+%s\tkept\t%s\n" "$i" "$COMMIT" "$SCORE" "$DELTA" "$DESCRIPTION" >> "$LOG_FILE"
    else
        echo "No improvement: $SCORE vs $BEST_SCORE ops/s ($DELTA) — reverting"
        git checkout -- src/
        printf "%s\t-\t%s\t%s\treverted\tno improvement\n" "$i" "$SCORE" "$DELTA" >> "$LOG_FILE"
    fi
done

echo ""
echo "========================================"
echo "=== Autoresearch complete ==="
echo "=== Baseline:    $BASELINE ops/s ==="
echo "=== Final best:  $BEST_SCORE ops/s ==="
TOTAL_DELTA=$(echo "$BEST_SCORE $BASELINE" | awk '{printf "%.3f", $1 - $2}')
TOTAL_PCT=$(echo "$BEST_SCORE $BASELINE" | awk '{printf "%.1f", (($1 - $2) / $2) * 100}')
echo "=== Improvement: +$TOTAL_DELTA ops/s ($TOTAL_PCT%) ==="
echo "========================================"
echo ""
echo "Results log: $LOG_FILE"
echo "Review kept commits: git log --oneline --grep='autoresearch'"
