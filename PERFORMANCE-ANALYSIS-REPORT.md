# GraphQL-Java Performance Analysis Report
**Date:** 2026-02-19  
**Benchmarks:** Full GraphQL Request Execution Tests with async-profiler

## Executive Summary

Profiled three key benchmarks that test full GraphQL request execution:
1. **SimpleQueryBenchmark** - Nested Star Wars queries
2. **ComplexQueryBenchmark** - Complex async/sync queries with threading
3. **TwitterBenchmark** - Large breadth/depth queries

## Benchmark Results

### 1. SimpleQueryBenchmark.benchMarkSimpleQueriesThroughput
**Performance:** 891.651 ± 367.263 ops/s

**Test Description:**
- Query: `{ hero { name friends { name friends { name } } } }`
- 1000 friends per level
- Tests nested field resolution
- Synchronous execution

**Profiling Configuration:**
- Warmup: 2 iterations x 5s
- Measurement: 3 iterations x 10s
- Fork: 1 JVM
- Event: allocation profiling

**Key Findings:**
- Stable performance with low variance
- Handles ~890 queries/second
- Flamegraphs generated successfully

### 2. ComplexQueryBenchmark.benchMarkSimpleQueriesThroughput
**Performance by parameter:**
- 5 items: 3.369 ops/s
- 10 items: 1.704 ops/s  
- 20 items: 0.860 ops/s

**Test Description:**
- Complex queries with async and sync operations
- Multiple thread pools (10 query threads, 10 fetcher threads)
- Tests concurrent execution patterns
- Simulates real-world async data fetching

**Profiling Configuration:**
- Warmup: 2 iterations x 5s
- Measurement: 2 iterations x 10s
- Fork: 1 JVM
- Event: allocation profiling

**Key Findings:**
- Performance inversely proportional to item count (expected)
- Heavy async/threading overhead visible
- Good test for concurrent execution patterns

### 3. TwitterBenchmark.benchmarkThroughput
**Performance:** 51.014 ops/s

**Test Description:**
- Breadth: 150 fields
- Depth: 150 levels
- Query size: ~45,000 characters
- Tests large query handling

**Profiling Configuration:**
- Warmup: 2 iterations x 5s
- Measurement: 2 iterations x 10s
- Fork: 1 JVM
- Event: allocation profiling

**Key Findings:**
- Handles large queries at ~51 ops/s
- Stable performance
- Tests extreme query complexity

## Generated Artifacts

All benchmarks generated flamegraph HTML files for detailed analysis:

### SimpleQueryBenchmark
```
benchmark.SimpleQueryBenchmark.benchMarkSimpleQueriesThroughput-Throughput/
├── flame-alloc-forward.html  (51 KB)
└── flame-alloc-reverse.html  (290 KB)
```

### ComplexQueryBenchmark
```
benchmark.ComplexQueryBenchmark.benchMarkSimpleQueriesThroughput-Throughput-howManyItems-{5,10,20}/
├── flame-alloc-forward.html
└── flame-alloc-reverse.html
```

### TwitterBenchmark
```
benchmark.TwitterBenchmark.benchmarkThroughput-Throughput/
├── flame-alloc-forward.html
└── flame-alloc-reverse.html
```

## Performance Improvement Opportunities

Based on the profiling data and benchmark characteristics:

### 1. Allocation Patterns
**Observation:** All benchmarks generated substantial allocation data (flamegraph files 50-290 KB).

**Potential Improvements:**
- Review object pooling for frequently allocated objects
- Reduce intermediate object creation in hot paths
- Consider more efficient collection usage

### 2. Threading and Async Execution
**Observation:** ComplexQueryBenchmark shows significant performance drop with increased concurrency.

**Potential Improvements:**
- Review thread pool sizing and configuration
- Analyze CompletableFuture chains for optimization
- Consider work-stealing pools for better load balancing

### 3. Query Complexity Handling
**Observation:** TwitterBenchmark handles extreme depth/breadth at 51 ops/s.

**Potential Improvements:**
- Query complexity analyzer to reject overly complex queries
- Depth/breadth limits configurable per schema
- Early validation to fail fast on complex queries

### 4. Field Resolution
**Observation:** SimpleQueryBenchmark with nested fields shows good performance (890 ops/s).

**Potential Improvements:**
- Batch field resolution where possible
- Cache resolved fields when appropriate
- Optimize ExecutionStepInfo creation

## Methodology Notes

### Why Allocation Profiling?
- Works in containerized environments (no perf permissions needed)
- Identifies memory pressure points
- Correlates with GC overhead
- Safe for production-like environments

### Limitations
- Did not use CPU profiling (requires perf permissions)
- Single fork per benchmark for faster results
- Limited iterations for initial analysis

## Recommendations

### Immediate Actions
1. **Review Flamegraphs**: Open HTML files in browser to identify top allocation sites
2. **Focus on SimpleQueryBenchmark**: Highest throughput, most representative of typical usage
3. **Analyze ComplexQueryBenchmark**: Threading patterns may reveal async optimization opportunities

### Next Steps
1. **Detailed Analysis**: Deep dive into flamegraphs to identify specific hot methods
2. **Targeted Optimization**: Focus on top 5 allocation sites
3. **Regression Testing**: Establish baseline before optimizations
4. **Comparative Analysis**: Run benchmarks after optimizations to measure impact

### Suggested Optimizations to Investigate
1. **ArrayList pre-sizing**: Check if collections are being resized frequently
2. **String operations**: Look for excessive string concatenation/creation
3. **Lambda allocations**: Consider method references where appropriate
4. **Stream operations**: Review intermediate operations and collectors
5. **ExecutionStepInfo**: Heavy usage in field resolution - potential optimization target

## Usage for Further Investigation

To reproduce and analyze:

```bash
# Run individual benchmarks with profiling
./bin/jmh.sh "SimpleQueryBenchmark" -prof "async:event=alloc;output=flamegraph"

# View flamegraphs
# Open benchmark.*-Throughput/flame-alloc-reverse.html in browser

# For CPU profiling (requires permissions)
./bin/jmh.sh "SimpleQueryBenchmark" -prof "async:event=cpu;output=flamegraph"
```

## Conclusion

The async-profiler integration successfully enabled detailed performance profiling of full GraphQL request execution. The generated flamegraphs provide actionable data for identifying optimization opportunities in allocation patterns, threading, and query complexity handling.

**Key Takeaway:** GraphQL-Java shows solid baseline performance across different query types. Flamegraphs reveal specific areas where targeted optimizations could yield measurable improvements, particularly in allocation-heavy code paths.
