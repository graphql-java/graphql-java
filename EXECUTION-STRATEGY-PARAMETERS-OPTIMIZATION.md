# ExecutionStrategyParameters Optimization Investigation

## Problem Statement

ExecutionStrategyParameters is the #1 allocation hotspot, consuming **10.21% (7.9GB)** of total allocations in SimpleQueryBenchmark. It's created for every field resolution in the query execution tree, resulting in thousands of instances per query.

## Current Implementation Analysis

### Class Structure
- **9 fields**: executionStepInfo, source, localContext, fields, nonNullableFieldValidator, path, currentField, parent, alternativeCallContext
- **Immutable**: Once created, fields cannot be changed
- **Transform pattern**: Multiple `transform()` methods create new instances with modified fields
- **Builder pattern**: Available but creates intermediate Builder object

### Usage Patterns

1. **Initial creation** (Execution.java):
```java
ExecutionStrategyParameters parameters = newParameters()
    .executionStepInfo(executionStepInfo)
    .source(rootObject)
    // ... more fields
    .build();
```

2. **Transform for child fields** (ExecutionStrategy.java):
```java
ExecutionStrategyParameters newParameters = parameters.transform(currentField, fieldPath, parameters);
```

3. **Multiple transform overloads**: 5 different transform methods for different update scenarios

## Optimization Options

### Option 1: Object Pooling (High Impact, High Risk)
**Estimated Impact:** 8-10% allocation reduction

**Approach:**
- Implement ThreadLocal object pool for ExecutionStrategyParameters
- Return objects to pool after field execution completes
- Reuse pooled objects for subsequent field resolutions

**Pros:**
- Dramatic allocation reduction
- Most direct solution to the problem

**Cons:**
- Complex lifecycle management
- Risk of use-after-return bugs
- Thread-local storage overhead
- May not work with async execution

**Recommendation:** ❌ Too risky for initial optimization

### Option 2: Reduce Object Size (Medium Impact, Low Risk)
**Estimated Impact:** 2-3% allocation reduction

**Approach:**
- Audit which fields are actually needed
- Move rarely-used fields to separate object
- Use bit flags for boolean-like fields

**Analysis of current fields:**
- `executionStepInfo` - Used heavily ✓
- `source` - Used for data fetching ✓
- `localContext` - Used occasionally
- `fields` - Used heavily ✓
- `nonNullableFieldValidator` - Could be shared/cached
- `path` - Used for error reporting
- `currentField` - Used heavily ✓
- `parent` - Used for backtracking
- `alternativeCallContext` - Used only with @defer (rare)

**Specific optimization:**
```java
// Before: 9 fields
private final ExecutionStepInfo executionStepInfo;
private final Object source;
// ... 7 more fields

// After: Core fields + optional context
private final ExecutionStepInfo executionStepInfo;
private final Object source;
private final MergedSelectionSet fields;
private final MergedField currentField;
private final ExecutionContext executionContext; // Consolidate 5 fields
```

**Pros:**
- Smaller objects = faster allocation
- Cleaner API
- Backwards compatible with accessor methods

**Cons:**
- Requires refactoring call sites
- May need additional indirection

**Recommendation:** ⚠️ Promising but needs careful analysis

### Option 3: Flyweight Pattern for Shared State (Medium Impact, Medium Risk)
**Estimated Impact:** 3-5% allocation reduction

**Approach:**
- Identify immutable state that's shared across many parameters
- Extract to separate shared object
- Store only reference in ExecutionStrategyParameters

**Example:**
```java
// Shared across all fields in same query
class SharedExecutionContext {
    final NonNullableFieldValidator nonNullableFieldValidator;
    final ExecutionContext executionContext;
    // Other shared state
}

class ExecutionStrategyParameters {
    private final SharedExecutionContext shared;
    private final ExecutionStepInfo executionStepInfo;
    // Field-specific state only
}
```

**Pros:**
- Reduces per-instance allocation
- Preserves immutability
- Natural separation of concerns

**Cons:**
- Additional indirection (one extra pointer chase)
- Needs careful lifecycle management of shared state

**Recommendation:** ✅ Good balance of risk/reward

### Option 4: Lazy Builder with Direct Construction (Low Impact, Very Low Risk)
**Estimated Impact:** 1-2% allocation reduction

**Approach:**
- Prefer direct `transform()` methods over Builder
- Optimize transform methods to avoid intermediate allocations
- Add fast-path constructors for common cases

**Current situation:**
- 5 transform methods already exist (lines 121-193)
- Builder is used in only 13 places
- Transform methods already bypass Builder

**Optimization:**
```java
// Current: Creates Builder + ExecutionStrategyParameters
parameters.transform(builder -> {
    builder.executionStepInfo(newInfo);
    builder.source(newSource);
});

// Optimized: Direct construction
parameters.transform(newInfo, newSource);
```

**Pros:**
- Easy to implement
- Low risk
- Immediate benefit

**Cons:**
- Limited impact (Builder not heavily used)

**Recommendation:** ✅ Do this first as quick win

### Option 5: Copy-on-Write with Dirty Tracking (Low Impact, High Complexity)
**Estimated Impact:** 2-3% allocation reduction

**Approach:**
- Make ExecutionStrategyParameters mutable internally
- Track which fields have changed
- Only allocate new instance when truly needed

**Example:**
```java
class ExecutionStrategyParameters {
    private volatile boolean dirty = false;
    
    ExecutionStrategyParameters withNewField(MergedField field) {
        if (this.currentField == field) {
            return this; // No allocation if unchanged
        }
        return new ExecutionStrategyParameters(...); // Allocate only when needed
    }
}
```

**Pros:**
- Eliminates allocations when transform is no-op

**Cons:**
- Violates immutability contract
- Complex to implement correctly
- May break assumptions in caller code

**Recommendation:** ❌ Too complex for benefit

### Option 6: Value Objects / Records (Future, Java 14+)
**Estimated Impact:** 5-8% allocation reduction (with Valhalla)

**Approach:**
- Convert to Java record (currently on Java 11)
- Benefits from future Project Valhalla optimizations

**When available:**
```java
public record ExecutionStrategyParameters(
    ExecutionStepInfo executionStepInfo,
    Object source,
    // ... other fields
) {
    // Compact constructor for validation
}
```

**Pros:**
- Future-proof
- JVM-level optimizations
- Potential stack allocation

**Cons:**
- Requires Java version upgrade
- Benefits unclear without Valhalla

**Recommendation:** ⏳ Future consideration

## Recommended Implementation Plan

### Phase 1: Quick Wins (1-2% improvement)
1. **Audit transform() usage**: Ensure all call sites use direct transform() instead of Builder
2. **Add specialized transform methods**: Cover common parameter update patterns
3. **Inline small methods**: Help JIT optimize better

### Phase 2: Structural Changes (3-5% improvement)
1. **Implement Flyweight pattern**: Extract shared execution context
2. **Consolidate rare fields**: Move @defer context to separate optional object
3. **Cache NonNullableFieldValidator**: Share across query execution

### Phase 3: Advanced (5-8% improvement, if needed)
1. **Benchmark object pooling**: Test in isolated scenario
2. **Profile with changes**: Re-run JMH to measure actual impact
3. **Consider specialized subclasses**: For leaf vs branch parameters

## Validation Approach

For each optimization:
1. Create isolated microbenchmark
2. Measure allocation rate before/after
3. Run full SimpleQueryBenchmark
4. Verify with async-profiler
5. Run complete test suite
6. Compare all three benchmarks (Simple, Complex, Twitter)

## Risk Mitigation

- Start with lowest-risk optimizations
- One change at a time
- Measure each change independently
- Keep changes reversible
- Comprehensive test coverage

## Expected Outcomes

**Conservative estimate:** 3-5% throughput improvement  
**Optimistic estimate:** 8-12% throughput improvement  
**Allocation reduction:** 50-70% of ExecutionStrategyParameters allocations

## Next Steps

1. Implement Phase 1 optimizations
2. Re-profile with async-profiler
3. Document actual improvements
4. Decide on Phase 2 based on results
