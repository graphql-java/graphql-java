# Schema generation on Java 25

This comparison measures `SchemaGenerator` and `FastSchemaGenerator` with all five
large-schema fixtures. It covers generation from an existing registry and parsing
followed by generation. Both generators perform schema validation and use
`RuntimeWiring.MOCKED_WIRING`; fixture file I/O is outside the measured operation.

The production baseline is `b88bb44f5eff9c520121806dec4ba2224ae4ad6d`, which already
includes the `TypeDefinitionRegistry` improvements in #4528. The measured baseline
jar adds only the benchmark changes in `ca944d6c1`. The optimized jar contains
the three subsequent optimization commits through `f32f89bd7`. Full source
revisions, jar hashes, fixture hashes, and printed-schema hashes are recorded in
[methodology.json](methodology.json).

## Final results

Mean time reductions range from **2.17% to 24.55%**, and allocation reductions
from **2.48% to 30.32%**. Read the variability and warmup qualifications below.

All values come from the final three-fork comparison. Time is ms/op.
Reduction = 100 × (1 − optimized / baseline); a negative value means an increase.
The JSON files retain all iteration samples, JMH confidence intervals, and fork means.

### Generation only

| Fixture | Generator | Baseline ms/op | Optimized ms/op | Time reduction | Allocation reduction |
| --- | --- | ---: | ---: | ---: | ---: |
| large-schema-1.graphqls | SchemaGenerator | 2.942 | 2.714 | 7.75% | 10.35% |
| large-schema-1.graphqls | FastSchemaGenerator | 1.806 | 1.725 | 4.53% | 3.52% |
| large-schema-2.graphqls | SchemaGenerator | 46.417 | 43.510 | 6.26% | 7.71% |
| large-schema-2.graphqls | FastSchemaGenerator | 30.690 | 29.230 | 4.76% | 4.51% |
| large-schema-3.graphqls | SchemaGenerator | 138.719 | 122.059 | 12.01% | 17.96% |
| large-schema-3.graphqls | FastSchemaGenerator | 79.652 | 73.683 | 7.49% | 10.80% |
| large-schema-4.graphqls | SchemaGenerator | 1813.420 | 1525.705 | 15.87% | 24.75% |
| large-schema-4.graphqls | FastSchemaGenerator | 989.209 | 864.881 | 12.57% | 18.69% |
| large-schema-5.graphqls | SchemaGenerator | 8855.376 | 6947.898 | 21.54% | 30.32% |
| large-schema-5.graphqls | FastSchemaGenerator | 4470.475 | 3372.870 | 24.55% | 29.36% |

### Parsing and generation

| Fixture | Generator | Baseline ms/op | Optimized ms/op | Time reduction | Allocation reduction |
| --- | --- | ---: | ---: | ---: | ---: |
| large-schema-1.graphqls | SchemaGenerator | 4.258 | 4.023 | 5.53% | 7.23% |
| large-schema-1.graphqls | FastSchemaGenerator | 3.170 | 3.051 | 3.77% | 2.91% |
| large-schema-2.graphqls | SchemaGenerator | 62.634 | 59.957 | 4.27% | 6.75% |
| large-schema-2.graphqls | FastSchemaGenerator | 47.435 | 46.405 | 2.17% | 2.48% |
| large-schema-3.graphqls | SchemaGenerator | 203.505 | 188.322 | 7.46% | 10.14% |
| large-schema-3.graphqls | FastSchemaGenerator | 145.046 | 136.595 | 5.83% | 6.19% |
| large-schema-4.graphqls | SchemaGenerator | 2276.871 | 2034.055 | 10.66% | 16.60% |
| large-schema-4.graphqls | FastSchemaGenerator | 1458.109 | 1332.142 | 8.64% | 10.76% |
| large-schema-5.graphqls | SchemaGenerator | 9617.202 | 7990.660 | 16.91% | 28.05% |
| large-schema-5.graphqls | FastSchemaGenerator | 5271.365 | 4228.057 | 19.79% | 21.83% |


[Baseline JMH data](final/baseline.json) and [optimized JMH data](final/optimized.json)
contain the raw iteration samples and JMH confidence intervals.
[comparison.json](comparison.json) also records each fork's mean. Small differences
must be read alongside this variation; fifteen iterations from three JVMs are not
fifteen independent JVM runs.
Allocation is JMH's `gc.alloc.rate.norm` in bytes per operation, not peak heap use.

## Changes and profile evidence

Three development rounds each used a complete timing matrix, separate CPU and
allocation profiling, a targeted optimization, and another complete timing matrix.
Each development timing case used one fork. Its data is retained in
[exploration/](exploration/) to show the development sequence; it is excluded from
the final gain calculation.

1. **Build breadcrumb lists only when requested.** Traversal previously copied an
   ancestor list for every context, even when schema generation never read it.
   The baseline schema-5 profiles attributed 20.20% of sampled allocated bytes
   to `DefaultTraverserContext` construction for `SchemaGenerator`, and 23.91% for
   `FastSchemaGenerator`. The change keeps the parent-node snapshot and builds an
   immutable list on demand. It also preserves custom parent-context behavior and
   avoids recursive list construction for deep traversals.
2. **Allocate traversal variable maps on the first write.** After the first change,
   the schema-5 allocation stacks attributed about 7.6% of allocated bytes to
   creating empty maps in `TraverserState.newContext`. Most contexts did not use
   them. Supplied maps remain live, ancestor lookup and shadowing stay dynamic,
   and parallel traversal keeps its existing map allocation behavior.
3. **Reduce child-list copying and unnecessary validation lookups.** After the
   second change, flattening child lists accounted for 7.70% of sampled allocated
   bytes in standard schema-5 generation. Pre-sizing the result and appending its
   children directly avoids temporary arrays and growth copies. The Fast schema-5
   profile still attributed 10.63% of CPU samples to `AppliedDirectivesAreValid`.
   That visitor now fetches inherited validation state only for nodes which have
   directives to validate. Schema validation remains enabled.

The profile percentages above are inclusive shares within individual recordings,
not measurements of time saved. CPU recordings include GC work. Summaries for
both generators, both scopes, and fixtures 4 and 5 are in [profiles/](profiles/).
Profiling used separate JMH forks with async-profiler 4.5 (CPU plus allocation
sampling), 10 × 10-second warmup, and 3 × 10-second recorded measurement iterations.
No profiler timings enter the final comparison.

## Measurement conditions

- Dedicated AMD Ryzen 5 3600: 6 physical cores, 12 logical CPUs, 64 GiB RAM.
- Ubuntu 26.04, Linux 7.0.0-22-generic; Ubuntu OpenJDK 25.0.4.1.
- JMH 1.37, one benchmark thread; three fresh JVM forks per version and case.
- Every fork warms up for 10 × 10 seconds, then measures for 5 × 10 seconds.
  Iterations finish their current operation, so the largest schemas take longer.
- A 24 GiB fixed heap, ZGC, compact object headers, adaptive GC workers, and
  pre-touching. Effective GC worker defaults were 8 parallel and 3 concurrent.
  ZGC disables compressed ordinary object pointers; compact headers and compressed
  class pointers were enabled. These are distinct JVM features.
- One performance workload at a time, protected by a host-wide exclusive lock.
  CPU governors were set to `performance`; RAID synchronization had finished and
  automatic package-update services were paused. Builds, tests, profile conversion,
  and large transfers did not overlap accepted benchmark runs.
- For each schema, the order of baseline and optimized runs alternates by benchmark
  method. This order reverses for the next schema. All three forks of one version
  finish before the other version runs. Timing runs use JMH's GC allocation
  counters, without async-profiler.

All 120 forks completed, and their GC logs contained no allocation-stall events.
The last five warmup iterations were screened for a coefficient of variation or
absolute fitted drift above 5%. One fork was flagged: optimized standard parsing
and generation for fixture 5 had -5.90% fitted warmup drift (2.51% variation).
Its last two warmups dipped, but measurement times rebounded instead of continuing
to fall. All original samples were retained; no iterations were removed. The
[review data](comparison-review.json) records all warmups and this inspection.
Environment snapshots confirmed consistent JVM flags, performance governors,
healthy RAID arrays, and no swap usage. The original governors and paused update
services were restored after measurement.

All 20 optimized means were lower, with no mean allocation increases. The smallest
latency difference, 2.17% for Fast parsing and generation on fixture 2, has
overlapping fork ranges and JMH 99.9% confidence intervals. Treat it as a measured
mean difference, not a separately established significant improvement.

Earlier G1 and 8 GiB ZGC trials were discarded. The 8 GiB heap caused allocation
stalls on the largest fixture. Smoke, profile, and one-fork development timings
are also excluded from the final comparison. During accepted 24 GiB development
runs, round 2 had one 108.701 ms allocation stall during warmup, before measurement;
the other three development timing matrices had none.

These results describe one machine, JVM/collector configuration, and five schemas
using mocked wiring. They do not establish performance for custom wiring, other
collectors or JVM versions, or other traversal workloads.

## Correctness checks

The final production code passed the Java 25 Spock suite with ZGC and compact
headers: 6,029 cases passed and 7 existing cases were skipped. Regression tests
cover breadcrumb snapshots and deep paths, live variable-map inheritance and
shadowing, child-list order and independence, and directive validation errors.
The baseline and all three candidates produced identical printed schemas for
both generators on all five fixtures. Registry type counts also stayed unchanged.

`assemble`, static/build checks, the existing TestNG compatibility suite, and
JaCoCo report generation passed. The local per-class coverage gate checked 993
classes and reported no regressions. TestNG reported 141 passing cases and 49 expected
skips. Local test counts include two pre-existing untracked parser test files
which are not part of this change.

## Reproduce the timing comparison

Build the JMH jar at `ca944d6c1` for the baseline and at `f32f89bd7` for the optimized
version using Java 25 and `./gradlew jmhJar`. Use separate checkouts and copy the
resulting `build/libs/*-jmh.jar` files to stable paths before starting measurements.
The first revision contains the same benchmark harness with unchanged production
code. Jar hashes identify the artifacts measured here; a fresh build may have
different archive metadata and therefore a different hash.

On an otherwise idle Linux host with enough memory for the 24 GiB heap plus JVM
and OS overhead, use Python 3.9 or later:

```sh
python3 reproduce.py \
  --baseline /path/to/baseline.jar \
  --optimized /path/to/optimized.jar \
  --output /path/to/new-results-directory \
  --lock /path/to/host-wide-benchmark.lock \
  --java /path/to/jdk-25/bin/java
```

The output directory must not already exist. Every other performance workload on
the host must honor the same lock file. The script runs all 40 version/case
combinations serially, records JMH JSON and logs, and retains GC logs. It uses the
same command-line settings and ordering as the original comparison, without
changing host services or CPU settings. Record and control those settings
separately, and restore any settings you change after benchmarking.

The complete JVM arguments are:

```text
-Xms24g -Xmx24g -XX:+UseZGC -XX:+UseCompactObjectHeaders
-XX:+UseDynamicNumberOfGCThreads -XX:+AlwaysPreTouch
-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints
-Xlog:gc:file=gc-%p.log:uptime,level,tags
```

For another profile-guided round, run a separate JMH invocation using one fork,
the same warmup, three measurement iterations, and the existing async-profiler
dependency (`ap-loader-all` 4.5-13). For example, append this profiler argument to
that separate invocation after locating its native library:

```text
-prof 'async:libPath=/path/to/libasyncProfiler.so;event=cpu;alloc=1m;output=jfr;dir=/path/to/profiles'
```

Do not run the profiler or convert its output while timing another benchmark.
