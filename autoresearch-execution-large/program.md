# Autoresearch: Optimize Execution Engine Performance (Large In-Memory Query)

## Goal

Improve the throughput (ops/sec) of `LargeInMemoryQueryPerformance.benchMarkSimpleQueriesThroughput` by making
targeted optimizations to the core execution engine. This benchmark executes a sync query returning 10M scalar
values — it cleanly isolates the execution engine (field resolution, result assembly, ResultNodesInfo).

Every improvement must pass the relevant test suite locally. Final full-suite verification happens on a clean EC2 instance.

## Metric

- **Primary**: `LargeInMemoryQueryPerformance.benchMarkSimpleQueriesThroughput` — higher is better (ops/sec)
- Run with: `./gradlew jmh -PjmhInclude="performance.LargeInMemoryQueryPerformance.benchMarkSimpleQueriesThroughput" -PjmhFork=1 -PjmhIterations=3 -PjmhWarmupIterations=2`
- A run takes ~3-5 minutes. Parse the score from JMH's output line containing `benchMarkSimpleQueriesThroughput`.
- **Use async-profiler** to identify hotspots before optimizing: add `-PjmhProfilers=async` to the JMH command. Output goes to `performance.LargeInMemoryQueryPerformance.benchMarkSimpleQueriesThroughput-Throughput/summary-cpu.txt`.

## Scope — Files You May Modify

Primary targets under `src/main/java/graphql/execution/`:

- `ExecutionStrategy.java` (1141 lines) — the main execution strategy, field resolution
- `AsyncExecutionStrategy.java` (97 lines) — async field execution
- `Execution.java` (328 lines) — top-level execution orchestration
- `FieldCollector.java` (182 lines) — collects fields from selection sets
- `ResultNodesInfo.java` (55 lines) — tracks result node info during execution
- `ExecutionStepInfoFactory.java` (92 lines) — creates step info per field
- `FetchedValue.java` (82 lines) — wraps fetched values
- `FieldValueInfo.java` (101 lines) — field value tracking
- `MergedSelectionSet.java` (73 lines) — merged selections
- `MergedField.java` — merged field representation

Also consider:
- `graphql/GraphQL.java` (624 lines) — top-level entry point
- `graphql/collect/ImmutableKit.java` — collection utilities
- `graphql/util/FpKit.java` — functional programming utilities
- `graphql/execution/instrumentation/` — instrumentation overhead

**Do NOT modify**: test files, benchmark files, schema files, build files.

## Constraints

1. **Relevant tests must pass locally**: Run `./gradlew test --tests "graphql.execution.*" --tests "graphql.GraphQLTest" -q` for fast iteration (~30 sec). Full suite runs on EC2.
2. **No new dependencies**: This is a firm project policy.
3. **No wildcard imports, no inner classes, no Optional**: Project coding standards.
4. **Preserve public API**: All `@PublicApi` method signatures must remain unchanged.
5. **Thread safety**: The execution engine is called concurrently. Don't introduce shared mutable state.
6. **Use `graphql.Assert`** not `Objects.requireNonNull`.

## Optimization Strategies to Explore (ordered by expected impact)

### High Impact
1. **Profile first**: Run async-profiler to identify actual CPU hotspots before making changes. The previous ENF autoresearch found that Guava ImmutableMap/ImmutableListMultimap builders were the dominant hotspot due to Object.hashCode() overhead — similar patterns may exist here.
2. **Reduce object allocation in the execution hot loop**: The execution strategy creates many intermediate objects per field (ExecutionStepInfo, FetchedValue, FieldValueInfo). Consider whether allocations can be reduced.
3. **Optimize ResultNodesInfo**: This is called for every field resolution. Any overhead here multiplies by the number of fields (10M in this benchmark).
4. **Replace Guava immutable builders with mutable collections**: If ImmutableMap.Builder or ImmutableList.Builder are used in hot paths, replacing with LinkedHashMap/ArrayList (as was done in the ENF optimization) can yield 20%+ improvements.
5. **Reduce instrumentation overhead**: Even "no-op" instrumentation has method call overhead per field.

### Medium Impact
6. **Optimize FieldCollector**: Field collection happens at each level. Caching or pre-computing merged selection sets could help.
7. **Reduce ExecutionStepInfo creation overhead**: ExecutionStepInfo is created per-field. Consider lazy computation of expensive fields.
8. **Avoid unnecessary wrapping/unwrapping**: FetchedValue wrapping, DataFetcherResult handling.
9. **Replace stream operations with loops**: In hot paths, `.stream().collect()` has overhead.

### Lower Impact (but easy wins)
10. **Pre-size collections**: When field count is known, pre-size ArrayList/HashMap.
11. **Cache repeated lookups**: Schema type lookups, field definition lookups.
12. **Reduce string operations**: String concatenation in hot paths.

## How to Iterate

1. **Profile first** with async-profiler to identify actual hotspots
2. Pick ONE strategy targeting the top hotspot
3. Make a focused, minimal change
4. Run tests locally: `./gradlew test --tests "graphql.execution.*" --tests "graphql.GraphQLTest" -q`
5. Run the benchmark — compare to previous best
6. If improved: commit with message "autoresearch: <description> [+X.XX ops/s]"
7. If not improved: revert with `git checkout -- src/`
8. Re-profile to see updated hotspots, then pick next strategy

## Lessons from Previous Autoresearch (ENF Optimization)

These patterns delivered the biggest wins in the ENF autoresearch:

- **ImmutableMap.Builder → LinkedHashMap**: Saved 20k ops/s. The `.build()` call hashes all keys, and Object.hashCode() on Apple Silicon triggers expensive `pthread_jit_write_protect_np`.
- **ImmutableListMultimap → parallel ArrayList**: Saved 22k ops/s. Same hashCode issue. Replaced keyed multimap with index-aligned parallel lists.
- **Avoid groupingBy when only checking group count**: Saved 13k ops/s. Replaced full map creation with a boolean flag.
- **Short-circuit for empty/single-element cases**: Multiple small wins from fast-pathing the common case.
- **Cache lambda captures**: Reusing a Supplier field instead of creating `() -> value` per call.

## Important Notes

- The benchmark queries 10M scalar fields — execution engine overhead per field is the bottleneck.
- `GraphQL.execute()` is the entry point; it calls `Execution.execute()` → `ExecutionStrategy.execute()`.
- The execution engine is inherently recursive (fields within fields).
- Guava is an existing dependency — you can use Guava utilities but nothing else new.
