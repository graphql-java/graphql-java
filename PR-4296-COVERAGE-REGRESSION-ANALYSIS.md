# PR 4296 — Code Coverage Regression Analysis

## Root Cause

The `markGeneratedEqualsHashCode` Gradle task (`build.gradle:601-639`) uses ASM bytecode
manipulation to inject `@Generated` annotations on **every** `equals(Object)` and `hashCode()`
method across all production classes. JaCoCo then excludes these methods from coverage reports.

The task was intended for "identity" equals/hashCode (trivial `super.equals()`/`super.hashCode()`
delegations whose coverage is non-deterministic due to hash collisions). However, the implementation
**blindly annotates all** `equals`/`hashCode` methods — including value-based ones with real
comparison logic.

## Why Coverage Drops

When JaCoCo excludes a method, it removes that method's lines/branches from **both** the numerator
(covered) and denominator (total). If the excluded methods were fully covered but the remaining
class code has lower average coverage, the class-level percentage **drops** — even though no
actual test coverage was lost.

## Per-Class Breakdown

| Class | Line Δ | Branch Δ | Method Δ | equals/hashCode tested? | Explanation |
|-------|--------|----------|----------|------------------------|-------------|
| **DefaultPageInfo** | -12.1% | -78.6% | -20.8% | Yes, fully | Small class (75 lines). `equals()` has 13 lines + many branches (null, type, 4 field checks). Removing these well-covered methods from a tiny class causes a massive percentage drop. |
| **StreamPayload** | -5.4% | -62.5% | -8.3% | Yes, fully | Small class. `equals()` contains most of the class's branch points (identity, null, super.equals, field). Both StreamPayload and parent IncrementalPayload have their methods excluded. |
| **QueryVisitorFieldEnvironmentImpl** | -3.7% | **+45.8%** | -2.4% | No | `equals`/`hashCode` compare 8 fields but are never directly tested. Removing **uncovered** branches improves branch %, while removing lines/methods lowers those ratios. |
| **MergedField** | -0.3% | **+3.6%** | -1.4% | No | Large class dilutes the effect. 4 methods excluded (outer + inner class). Untested equals/hashCode had uncovered branches. |
| **ResultPath** | -0.8% | **+0.8%** | -1.7% | No (implicit only) | Large class (358 lines). Non-trivial loop-based equals/hashCode. Only exercised implicitly. Small effect due to class size. |

## Key Insight

The direction of the change reveals whether equals/hashCode was well-tested:

- **DefaultPageInfo, StreamPayload**: Coverage **drops** across all metrics → the excluded methods
  were **better covered** than the rest of the class
- **QueryVisitorFieldEnvironmentImpl, MergedField, ResultPath**: Branch coverage **increases** →
  the excluded methods were **less covered** (had uncovered branches)

## Recommendation

The `markGeneratedEqualsHashCode` task should be narrowed to only annotate **identity**
equals/hashCode implementations (those that simply delegate to `super.equals()`/`super.hashCode()`).
Value-based implementations like `DefaultPageInfo.equals()` and `StreamPayload.equals()` contain
real comparison logic that should remain in coverage reports.

Alternatively, the task could be removed entirely and the per-class coverage gate threshold
could be slightly loosened to account for the non-deterministic nature of identity equals/hashCode
coverage.
