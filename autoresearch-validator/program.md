# Autoresearch: Optimize Validator Performance (Overlapping Field Validation)

## Goal

Improve the throughput (ops/sec) of `OverlappingFieldValidationPerformance.overlappingFieldValidationThroughput`
by making targeted optimizations to the validation engine. Validation runs on every query, so improvements here
have broad impact. The benchmark tests overlapping field validation with a large schema and query, plus several
generated scenarios (repeated fields, fragments, deep abstract/concrete types).

Every improvement must pass the relevant test suite locally. Final full-suite verification happens on a clean EC2 instance.

## Metric

- **Primary**: `OverlappingFieldValidationPerformance.overlappingFieldValidationThroughput` — higher is better (ops/sec)
- Run with: `./gradlew jmh -PjmhInclude="performance.OverlappingFieldValidationPerformance.overlappingFieldValidationThroughput" -PjmhFork=1 -PjmhIterations=3 -PjmhWarmupIterations=2`
- **Use async-profiler** to identify hotspots: add `-PjmhProfilers=async` to the JMH command.
- Additional benchmarks for cross-validation: `benchmarkRepeatedFields`, `benchmarkOverlapFrag`, `benchmarkDeepAbstractConcrete` etc.

## Scope — Files You May Modify

Primary targets under `src/main/java/graphql/validation/`:

- `OperationValidator.java` (1785 lines) — the main validation logic, including overlapping fields check. **This is the primary target.**
- `Validator.java` (54 lines) — top-level validator entry point
- `LanguageTraversal.java` (44 lines) — AST traversal for validation
- `TraversalContext.java` — maintains type context during traversal
- `ValidationContext.java` — validation context
- `ValidationErrorCollector.java` — error collection
- `DocumentVisitor.java` — visitor interface

Also consider utility classes:
- `graphql/collect/ImmutableKit.java` — collection utilities
- `graphql/util/FpKit.java` — functional programming utilities
- `graphql/language/AstComparator.java` — AST comparison (used in field merging checks)

**Do NOT modify**: test files, benchmark files, schema files, build files.

## Constraints

1. **Relevant tests must pass locally**: Run `./gradlew test --tests "graphql.validation.*" -q` for fast iteration (~10 sec). Full suite runs on EC2.
2. **No new dependencies**: This is a firm project policy.
3. **No wildcard imports, no inner classes, no Optional**: Project coding standards.
4. **Preserve public API**: All `@PublicApi` method signatures must remain unchanged.
5. **Thread safety**: The validator may be called concurrently. Don't introduce shared mutable state.
6. **Use `graphql.Assert`** not `Objects.requireNonNull`.

## Optimization Strategies to Explore (ordered by expected impact)

### High Impact
1. **Profile first**: Run async-profiler to identify actual CPU hotspots. The overlapping field validation in OperationValidator is known to have O(n^2) or worse complexity in some cases.
2. **Reduce algorithmic complexity in overlapping field checks**: The `OVERLAPPING_FIELDS_CAN_BE_MERGED` rule compares pairs of fields. Memoization, caching of comparison results, or smarter traversal order can reduce redundant work.
3. **Replace Guava immutable builders with mutable collections in hot paths**: ImmutableMap.Builder and ImmutableList.Builder have expensive hashCode overhead during build(). This was the #1 finding in the ENF optimization.
4. **Cache field-pair comparison results**: If the same field pairs are compared repeatedly across different contexts, cache the results.

### Medium Impact
5. **Optimize AstComparator usage**: Field merging checks use AST comparison. If the same AST nodes are compared multiple times, caching helps.
6. **Reduce object allocation in validation traversal**: Each visited node may create validation state objects.
7. **Optimize type resolution during validation**: Type lookups for overlapping field checks.
8. **Early termination**: Skip validation checks that can't apply to the current node type.

### Lower Impact (but easy wins)
9. **Pre-size collections**: When the number of fields/fragments is known.
10. **Replace stream operations with loops** in hot paths.
11. **Cache schema type lookups** that are repeated during validation.
12. **Reduce string operations**: Error message construction in non-error paths.

## How to Iterate

1. **Profile first** with async-profiler to identify actual hotspots
2. Pick ONE strategy targeting the top hotspot
3. Make a focused, minimal change
4. Run tests locally: `./gradlew test --tests "graphql.validation.*" -q`
5. Run the benchmark — compare to previous best
6. If improved: commit with message "autoresearch: <description> [+X.XX ops/s]"
7. If not improved: revert with `git checkout -- src/`
8. Re-profile to see updated hotspots, then pick next strategy

## Lessons from Previous Autoresearch (ENF Optimization)

- **ImmutableMap.Builder → LinkedHashMap**: Saved 20k ops/s due to Object.hashCode() overhead.
- **ImmutableListMultimap → parallel ArrayList**: Saved 22k ops/s. Same hashCode issue.
- **Avoid groupingBy when only checking group count**: Replaced full map with boolean flag.
- **Short-circuit for empty/single-element cases**: Multiple small wins.
- **Cache lambda captures**: Reuse Supplier fields instead of per-call lambdas.
- **Profile-guided optimization**: The biggest wins came from profiling, not guessing.

## Important Notes

- `OperationValidator.java` at 1785 lines is the main target. It implements all validation rules.
- The overlapping fields check (`OVERLAPPING_FIELDS_CAN_BE_MERGED`) is the most expensive rule and is specifically what the benchmark tests.
- The benchmark uses `@Param({"100"})` for size, generating queries with 100 fields/fragments.
- The validation runs `LanguageTraversal.traverse(document, operationValidator)` which walks the AST.
- Guava is an existing dependency — you can use Guava utilities but nothing else new.
