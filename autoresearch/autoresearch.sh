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
#   - Claude Code CLI installed and authenticated (`claude` on PATH)
#   - Java toolchain (JDK 25) available for builds
#   - Run from the graphql-java project root
#
# Permissions:
#   Instead of `--dangerously-skip-permissions`, the script uses `--allowedTools`
#   with an explicit allowlist of safe, read/write-only tools:
#     - Read: read file contents (no side effects)
#     - Edit: edit file contents (reversible via git checkout)
#     - Glob: find files by pattern (no side effects)
#     - Grep: search file contents (no side effects)
#   This is safer than blanket permission bypass because:
#     - No Bash/shell access: the agent cannot run arbitrary commands
#     - No Write tool: the agent cannot create new files, only edit existing ones
#     - No network access: no web fetches or external calls
#     - Tests gate every change (bad edits get reverted by the outer harness)
#     - Git tracks everything
#
# The loop:
#   1. Get baseline benchmark score
#   2. Ask Claude (Sonnet) to make ONE optimization
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

Read autoresearch/program.md for full context and strategy.

Current best benchmark score: $BEST_SCORE ops/s (baseline was: $BASELINE ops/s)

Previous optimization log (last 10 entries):
$RECENT_LOG

YOUR TASK: Make exactly ONE focused optimization to the ENF code.
- Read the code files first, then pick the most promising strategy from program.md
  that has NOT already been tried (check the log above)
- Make a minimal, targeted change to ONE or TWO files
- Do NOT run tests or benchmarks — the outer harness handles that
- Do NOT commit — the outer harness handles that
- After editing, output a single-line summary of what you changed and why

SCOPE: Only modify files under src/main/java/graphql/normalized/ or the utility
files listed in program.md (ImmutableKit.java, FpKit.java).

Make the change now."

    # Run Claude in non-interactive mode with file editing capability
    # --allowedTools: explicit allowlist of safe tools (read, edit, search — no shell)
    # --model sonnet: fast iterations
    # --max-turns 20: enough to read files + make edits, but bounded
    echo "--- Asking Claude to make an optimization ---"
    CLAUDE_OUTPUT=$(claude \
        --model sonnet \
        --allowedTools "Read" "Edit" "Glob" "Grep" \
        --max-turns 20 \
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

    # Run tests (skip benchmarks in run_benchmark.sh — run tests separately for speed)
    echo "--- Running tests ---"
    if ! ./gradlew test -q 2>&1 | tail -10; then
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

        # Get a description of the change from git diff
        DESCRIPTION=$(git diff --stat src/main/java/ | tail -1 | xargs)

        # Commit the improvement
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
