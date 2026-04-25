# Autoresearch: Optimize Execution Engine Performance (Complex Async Query)

## Goal

Improve the throughput (ops/sec) of `ComplexQueryPerformance.benchMarkSimpleQueriesThroughput` by making
targeted optimizations to the core execution engine. This benchmark executes a complex query with both
sync and async data fetchers, multiple threads, and nested object types (shops → departments → products,
each with 12 fields). It exercises the full execution pipeline including async completion handling.

Every improvement must pass the relevant test suite locally. Final full-suite verification happens on a clean EC2 instance.

## Metric

- **Primary**: `ComplexQueryPerformance.benchMarkSimpleQueriesThroughput` — higher is better (ops/sec)
- Run with: `./gradlew jmh -PjmhInclude="performance.ComplexQueryPerformance.benchMarkSimpleQueriesThroughput" -PjmhFork=1 -PjmhIterations=3 -PjmhWarmupIterations=2`
- Note: This benchmark has a `@Param({"5", "10", "20"})` for `howManyItems`. The default JMH run will test all three. For faster iteration during development, you can filter to just one param.
- **Use async-profiler** to identify hotspots: add `-PjmhProfilers=async` to the JMH command.

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
- `Async.java` — async execution utilities

Also consider:
- `graphql/GraphQL.java` (624 lines) — top-level entry point
- `graphql/collect/ImmutableKit.java` — collection utilities
- `graphql/util/FpKit.java` — functional programming utilities
- `graphql/execution/instrumentation/` — instrumentation overhead

**Do NOT modify**: test files, benchmark files, schema files, build files.

## Constraints

1. **Relevant tests must pass locally**: Run `./gradlew test --tests "graphql.execution.*" --tests "graphql.GraphQLTest" -q` for fast iteration. Full suite runs on EC2.
2. **No new dependencies**: This is a firm project policy.
3. **No wildcard imports, no inner classes, no Optional**: Project coding standards.
4. **Preserve public API**: All `@PublicApi` method signatures must remain unchanged.
5. **Thread safety**: The execution engine is called concurrently. Don't introduce shared mutable state.
6. **Use `graphql.Assert`** not `Objects.requireNonNull`.

## Optimization Strategies to Explore (ordered by expected impact)

### High Impact
1. **Profile first**: Run async-profiler to identify actual CPU hotspots before making changes.
2. **Reduce CompletableFuture overhead**: The async path creates many CompletableFuture chains. Consider whether composition can be simplified.
3. **Optimize field resolution dispatch**: Each field goes through ExecutionStrategy which has overhead for instrumentation, error handling, etc. Batch processing or reducing per-field overhead helps.
4. **Replace Guava immutable builders with mutable collections in hot paths**: ImmutableMap.Builder and ImmutableList.Builder have expensive hashCode overhead during build().
5. **Reduce instrumentation overhead**: Even "no-op" instrumentation dispatches per field.

### Medium Impact
6. **Optimize FieldCollector for repeated patterns**: The query has repeated field patterns across shops/departments/products.
7. **Reduce ExecutionStepInfo creation overhead**: Created per-field, consider lazy computation.
8. **Optimize DataFetcherResult handling**: Unwrapping overhead per field.
9. **Reduce lock contention in async paths**: If multiple threads contend on shared state.

### Lower Impact (but easy wins)
10. **Pre-size collections**: When field count is known.
11. **Cache repeated type/field lookups**.
12. **Replace stream operations with loops** in hot paths.

## How to Iterate

1. **Profile first** with async-profiler to identify actual hotspots
2. Pick ONE strategy targeting the top hotspot
3. Make a focused, minimal change
4. Run tests locally: `./gradlew test --tests "graphql.execution.*" --tests "graphql.GraphQLTest" -q`
5. Run the benchmark — compare to previous best
6. If improved: commit
7. If not improved: revert with `git checkout -- src/`
8. Re-profile, then pick next strategy

## Lessons from Previous Autoresearch (ENF Optimization)

- **ImmutableMap.Builder → LinkedHashMap**: Saved 20k ops/s due to Object.hashCode() overhead.
- **ImmutableListMultimap → parallel ArrayList**: Saved 22k ops/s. Same hashCode issue.
- **Avoid groupingBy when only checking group count**: Replaced full map with boolean flag.
- **Short-circuit for empty/single-element cases**: Multiple small wins.
- **Cache lambda captures**: Reuse Supplier fields instead of per-call lambdas.

## Important Notes

- This benchmark involves **multiple threads** (10 query threads + 10 fetcher threads). Be careful with thread safety.
- The async data fetchers include `Thread.sleep()` to simulate real-world latency. Optimizations to the execution engine reduce the non-sleep overhead.
- Guava is an existing dependency — you can use Guava utilities but nothing else new.
