# Performance Profiling with JMH and async-profiler

This document describes how to run JMH benchmarks with async-profiler for detailed performance analysis.

## Prerequisites

- JDK 11 or higher
- Linux, macOS, or Windows (with appropriate native library support)

## Quick Start

### Using the JMH Script

The simplest way to run benchmarks with profiling is to use the provided script:

```bash
./bin/jmh.sh <benchmark-pattern> [JMH options] -prof async
```

### Examples

1. **Basic profiling with allocation tracking** (works without special permissions):
   ```bash
   ./bin/jmh.sh "ComplexQueryBenchmark" -prof "async:event=alloc"
   ```

2. **Generate flamegraphs**:
   ```bash
   ./bin/jmh.sh "ComplexQueryBenchmark" -prof "async:event=alloc;output=flamegraph"
   ```

3. **Profile a specific benchmark method**:
   ```bash
   ./bin/jmh.sh "benchmark.GetterAccessBenchmark.measureDirectAccess" -wi 2 -i 3 -f 1 -prof "async:event=alloc;output=flamegraph"
   ```

4. **CPU profiling** (requires perf permissions on Linux):
   ```bash
   ./bin/jmh.sh "ComplexQueryBenchmark" -prof "async:event=cpu;output=flamegraph"
   ```

## Using Gradle

You can also run benchmarks directly with Gradle:

```bash
# Run all benchmarks
./gradlew jmh

# Run specific benchmark
./gradlew jmh -PjmhInclude="ComplexQueryBenchmark"

# Run with profiler (note: async-profiler setup is handled by bin/jmh.sh)
./gradlew jmh -PjmhProfilers="gc"
```

## async-profiler Options

The async-profiler supports many configuration options. Here are the most useful ones:

### Event Types

- `event=cpu` - CPU profiling (default, requires perf permissions)
- `event=alloc` - Allocation profiling (works without special permissions)
- `event=lock` - Lock contention profiling
- `event=wall` - Wall-clock profiling

### Output Formats

- `output=text` - Text summary (default)
- `output=flamegraph` - Interactive HTML flamegraph
- `output=tree` - Call tree format
- `output=jfr` - Java Flight Recorder format

### Other Options

- `interval=<value>` - Sampling interval (e.g., `interval=1ms`)
- `alloc=<bytes>` - Allocation sampling interval
- `libPath=<path>` - Path to libasyncProfiler.so (auto-configured by script)

### Combining Options

Options are separated by semicolons:

```bash
./bin/jmh.sh "MyBenchmark" -prof "async:event=alloc;output=flamegraph;interval=1ms"
```

## Understanding the Output

### Flamegraphs

When using `output=flamegraph`, async-profiler generates interactive HTML files in a directory named after your benchmark. For example:

```
benchmark.ComplexQueryBenchmark.benchMarkSimpleQueriesThroughput-Throughput/
├── flame-alloc-forward.html  # Top-down view
└── flame-alloc-reverse.html  # Bottom-up view
```

Open these files in a web browser to explore:
- Width of bars = proportion of time/allocations
- Click on any frame to zoom in
- Hover to see details
- Use browser back/forward to navigate

### Text Output

Text output shows a summary of the top methods/allocation sites:

```
--- Execution profile ---
Total samples       : 12345

       bytes  percent  samples  top
  ----------  -------  -------  ---
      500000   40.55%     5000  java.util.HashMap.putVal
      300000   24.33%     3000  java.lang.String.substring
      ...
```

## Common Profiling Scenarios

### 1. Find Memory Allocation Hotspots

```bash
./bin/jmh.sh "MyBenchmark" -prof "async:event=alloc;output=flamegraph"
```

Look at the generated flamegraph to identify which methods allocate the most memory.

### 2. Find CPU Hotspots

On systems with perf permissions:

```bash
./bin/jmh.sh "MyBenchmark" -prof "async:event=cpu;output=flamegraph"
```

If you don't have perf permissions, you'll see an error. In containerized environments like CI/CD, use allocation profiling instead.

### 3. Profile All Benchmarks

```bash
./bin/jmh.sh -prof "async:event=alloc;output=flamegraph"
```

This will run all benchmarks and generate separate flamegraphs for each.

### 4. Quick Performance Check

For a quick overview without generating files:

```bash
./bin/jmh.sh "MyBenchmark" -prof "async:event=alloc"
```

## Limitations

### Permission Requirements

CPU profiling requires access to performance counters:
- On Linux: `kernel.perf_event_paranoid` should be set to 1 or less
- In containers/CI: CPU profiling may not work; use allocation profiling instead
- On macOS: Should work without special permissions
- On Windows: Limited support

### Supported Platforms

async-profiler includes native libraries for:
- Linux x64
- Linux ARM64
- macOS (x64 and ARM64)

## Troubleshooting

### "No access to perf events" Error

This error occurs when trying to use CPU profiling without proper permissions. Solutions:

1. Use allocation profiling instead:
   ```bash
   -prof "async:event=alloc"
   ```

2. On Linux, adjust kernel settings (requires root):
   ```bash
   sudo sysctl kernel.perf_event_paranoid=1
   ```

### "Unable to load async-profiler" Error

If the script cannot find the async-profiler library:

1. Make sure you're using the `bin/jmh.sh` script, which handles library extraction
2. If running manually, extract the library and set `LD_LIBRARY_PATH`:
   ```bash
   export LD_LIBRARY_PATH=/path/to/async-profiler/lib:$LD_LIBRARY_PATH
   ```

### Empty or Missing Output

If profiling generates no data:
- Increase warmup and measurement iterations: `-wi 5 -i 10`
- Check that the benchmark actually executes work
- Try a different event type (e.g., `alloc` instead of `cpu`)

## Additional Resources

- [JMH Documentation](https://github.com/openjdk/jmh)
- [async-profiler Documentation](https://github.com/async-profiler/async-profiler)
- [Flamegraph Interpretation Guide](http://www.brendangregg.com/flamegraphs.html)

## Examples in This Repository

The repository includes several benchmarks you can profile:

- `ComplexQueryBenchmark` - Complex GraphQL query execution
- `GetterAccessBenchmark` - Method access performance
- `AsyncBenchmark` - Asynchronous execution patterns
- `ChainedInstrumentationBenchmark` - Instrumentation overhead
- `CreateExtendedSchemaBenchmark` - Schema creation performance

Try profiling these to understand where graphql-java spends time and allocates memory!
