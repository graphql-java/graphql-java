# Hotspot Analysis from JMH Profiling

## Executive Summary

Analysis of allocation profiling data from SimpleQueryBenchmark reveals several significant hotspots where targeted optimizations could yield measurable performance improvements.

## Methodology

Ran SimpleQueryBenchmark with async-profiler allocation tracking:
- 903.495 ± 213.207 ops/s baseline performance
- Total allocations analyzed: 77.6 GB across test run
- Focus on allocation sites >1% of total

## Top Allocation Hotspots

### 1. ExecutionStrategyParameters (10.21% - 7.9GB)

**Location:** `graphql.execution.ExecutionStrategyParameters`

**Issue:** This object is created for every field resolution in the query execution tree. With nested queries, this creates thousands of instances per query.

**Current Implementation:**
```java
private ExecutionStrategyParameters(ExecutionStepInfo executionStepInfo,
                                    Object source,
                                    Object localContext,
                                    MergedSelectionSet fields,
                                    NonNullableFieldValidator nonNullableFieldValidator,
                                    ResultPath path,
                                    MergedField currentField,
                                    ExecutionStrategyParameters parent,
                                    AlternativeCallContext alternativeCallContext) {
    this.executionStepInfo = assertNotNull(executionStepInfo, "executionStepInfo is null");
    // ... 8 more field assignments
}
```

**Optimization Opportunities:**
1. **Object Pooling**: Consider pooling ExecutionStrategyParameters objects for reuse
2. **Reduce Field Count**: Review if all 9 fields are necessary or if some can be computed on-demand
3. **Flyweight Pattern**: Share immutable state across instances where possible

**Impact Estimate:** 2-3% throughput improvement

### 2. LinkedHashMap + LinkedHashMap$Entry (11.68% combined - 13GB)

**Location:** Various (field arguments, variable maps, selection sets)

**Issue:** LinkedHashMap is used throughout execution but often with small, known-size collections.

**Optimization Opportunities:**
1. **Pre-size collections**: When size is known, initialize with capacity
2. **Use ArrayList for small sets**: For <5 items, ArrayList may be faster
3. **Immutable collections**: Use ImmutableMap for read-only data

**Example Fix:**
```java
// Before:
Map<String, Object> args = new LinkedHashMap<>();

// After (if size known):
Map<String, Object> args = new LinkedHashMap<>((int) (expectedSize / 0.75) + 1);
```

**Impact Estimate:** 1-2% throughput improvement

### 3. ExecutionStepInfo (5.49% - 4.2GB)

**Location:** `graphql.execution.ExecutionStepInfo`

**Issue:** Created for every field in the execution tree. Has 8 fields including Supplier for arguments.

**Current Allocation Pattern:**
- Created via Builder pattern
- Alternative constructor exists but not heavily used
- Contains `Supplier<ImmutableMapWithNullValues<String, Object>> arguments`

**Optimization Opportunities:**
1. **Prefer direct constructor**: Line 84-98 shows optimized constructor (~1% faster)
2. **Lazy argument resolution**: Arguments supplier allocates IntraThreadMemoizedSupplier
3. **Cache common instances**: Root-level ExecutionStepInfo could be cached

**Impact Estimate:** 1-2% throughput improvement

### 4. ResultPath (3.38% - 2.6GB)

**Location:** `graphql.execution.ResultPath`

**Issue:** Creates new path object for each field traversal. Immutable with parent reference.

**Current Implementation:**
```java
private ResultPath(ResultPath parent, String segment) {
    this.parent = assertNotNull(parent, "Must provide a parent path");
    this.segment = assertNotNull(segment, "Must provide a sub path");
    this.toStringValue = initString();  // ← String allocation
    this.level = parent.level + 1;
}
```

**Optimization Opportunities:**
1. **Lazy toString()**: `toStringValue` is computed eagerly but may not be used
2. **Path interning**: Common paths could be cached/interned
3. **StringBuilder pooling**: String building could use pooled StringBuilder

**Impact Estimate:** 0.5-1% throughput improvement

### 5. IntraThreadMemoizedSupplier (3.34% - 2.5GB)

**Location:** `graphql.util.IntraThreadMemoizedSupplier`

**Issue:** Created for every lazy-evaluated value, particularly in ExecutionStepInfo for arguments.

**Current Implementation:**
```java
private T value = (T) SENTINEL;
private final Supplier<T> delegate;
```

**Optimization Opportunities:**
1. **Avoid for already-resolved values**: If value is known, skip memoization wrapper
2. **Direct value storage**: For hot paths, store value directly instead of wrapping
3. **Reuse wrapper instances**: Pool for common access patterns

**Impact Estimate:** 0.5-1% throughput improvement

### 6. String and byte[] (15.9% combined - 12.2GB)

**Location:** Throughout codebase

**Issue:** String operations, particularly in path construction and error messages.

**Optimization Opportunities:**
1. **Reduce toString() calls**: Many classes compute string representation eagerly
2. **String interning**: For common field names and type names
3. **Avoid string concatenation**: Use StringBuilder for multi-part strings
4. **Lazy error message construction**: Only build error strings when actually needed

**Impact Estimate:** 2-3% throughput improvement

## Recommended Implementation Priority

### High Impact, Low Risk (Implement First)
1. **Pre-size LinkedHashMap collections** - Easy win, low risk
2. **Lazy ResultPath.toStringValue** - Simple change, measurable impact
3. **Avoid IntraThreadMemoizedSupplier for known values** - Clear optimization

### Medium Impact, Medium Risk
4. **Optimize ExecutionStepInfo construction** - Use direct constructor more
5. **Cache common ExecutionStepInfo instances** - Requires careful lifecycle management
6. **String interning for field/type names** - Needs memory analysis

### High Impact, High Risk (Requires Deep Analysis)
7. **Object pooling for ExecutionStrategyParameters** - Complex lifecycle
8. **Flyweight pattern for shared state** - Significant architectural change

## Validation Methodology

For each optimization:
1. Create isolated microbenchmark
2. Run with and without optimization
3. Verify with allocation profiler
4. Run full test suite
5. Compare before/after on all three benchmarks

## Next Steps

1. Implement top 3 optimizations
2. Re-run profiling to measure impact
3. Document actual vs estimated improvements
4. Iterate on remaining opportunities

