# Autoresearch: Optimize ExecutableNormalizedOperationFactory Performance

## Goal

Improve the throughput (ops/sec) of `ENF1Performance.benchMarkThroughput` by making
targeted optimizations to the ENF creation pipeline. Every improvement must pass the
full test suite.

## Metric

- **Primary**: `ENF1Performance.benchMarkThroughput` — higher is better (ops/sec)
- Run with: `./gradlew jmhRun -PjmhInclude="performance.ENF1Performance.benchMarkThroughput" -PjmhFork=1 -PjmhIterations=3 -PjmhWarmupIterations=2`
- A run takes ~2-3 minutes. Parse the score from JMH's output line containing `benchMarkThroughput`.

## Scope — Files You May Modify

Only modify files under `src/main/java/graphql/normalized/`:

- `ExecutableNormalizedOperationFactory.java` (959 lines) — the main target
- `ENFMerger.java` (197 lines) — post-processing merge step
- `ExecutableNormalizedField.java` (700 lines) — the field data class
- `ExecutableNormalizedOperation.java` (199 lines) — the result container
- Supporting: `ArgumentMaker.java`, `NormalizedInputValue.java`, etc.

Also consider utility classes these depend on:
- `graphql/collect/ImmutableKit.java`
- `graphql/util/FpKit.java`

**Do NOT modify**: test files, benchmark files, schema files, build files.

## Constraints

1. **All tests must pass**: Run `./gradlew test` before benchmarking. If tests fail, revert.
2. **No new dependencies**: This is a firm project policy.
3. **No wildcard imports, no inner classes, no Optional**: Project coding standards.
4. **Preserve public API**: All `@PublicApi` method signatures must remain unchanged.
5. **Thread safety**: The factory is called concurrently. Don't introduce shared mutable state.
6. **Use `graphql.Assert`** not `Objects.requireNonNull`.

## Optimization Strategies to Explore (ordered by expected impact)

### High Impact
1. **Reduce object allocation in hot loops**: `buildEnfsRecursively()` and `collectFromSelectionSet()` create many intermediate collections (ArrayList, LinkedHashSet, LinkedHashMap). Consider pre-sizing or reusing.
2. **Avoid unnecessary Set/Map copies**: `groupByCommonParents()` creates grouped collections that could be more efficient.
3. **Replace stream operations with loops**: In hot paths, `.stream().collect()` has overhead from lambda allocation and iterator creation. Simple for-loops are faster.
4. **ImmutableListMultimap.Builder overhead**: The builders accumulate entries one-by-one. Consider whether bulk operations are possible.

### Medium Impact
5. **Cache type lookups**: `Introspection.getFieldDef()` and `schema.getImplementations()` are called repeatedly for the same types. A local cache per factory invocation could help.
6. **Optimize ENFMerger**: The merge step does O(n) scans. Consider whether merge candidates can be identified during collection rather than post-processing.
7. **Lazy QueryDirectives creation**: Only create `QueryDirectivesImpl` when directives are actually present on a field.
8. **Reduce LinkedHashSet usage**: Where insertion order doesn't matter, plain HashSet is faster.

### Lower Impact (but easy wins)
9. **Pre-size collections**: When the approximate size is known (e.g., number of selections), pre-size ArrayList/HashMap.
10. **Avoid unnecessary wrapping**: e.g., `Collections.singleton()` vs direct iteration.
11. **StringBuilder for string concatenation** in any hot-path string building.

## How to Iterate

1. Pick ONE strategy from above (start with #1)
2. Make a focused, minimal change
3. Run `./gradlew test` — if it fails, revert immediately
4. Run the benchmark — compare to previous best
5. If improved: commit with message "autoresearch: <description> [+X.XX ops/s]"
6. If not improved: revert with `git checkout -- src/`
7. Move to next strategy

## Important Notes

- The factory creates a **new instance per call** (no shared state between invocations), so per-invocation caching is safe.
- `ExecutableNormalizedField` is intentionally `@Mutable` — the factory builds it up incrementally.
- The `ImmutableListMultimap` and `ImmutableMap` builders are finalized only at the end in the factory's constructor.
- Guava is an existing dependency — you can use Guava utilities but nothing else new.
- The `CollectedField`, `CollectedFieldGroup`, and `PossibleMerger` inner records are allocation-heavy — they're created per-field during traversal.
