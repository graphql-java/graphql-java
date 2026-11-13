# Direct SDL checking and schema construction follow-up

The follow-up directly reduces SDL validation and directive construction work used
by both `SchemaGenerator` and `FastSchemaGenerator`. Mean time is lower in 19 of
20 final cases (1.18-43.04%); the remaining case is 0.23% higher and near-neutral
within observed variability. Allocation is lower in all 20 cases (1.62-61.84%).
Small timing differences and the two warmup flags need the qualifications below.

The baseline is `baf539554c9092ea8434cbced868a40d545a736b`: the previously published
traversal changes plus merged master `96443c271`. Master correctness fixes are
present in every follow-up variant. A (`81176201b2ad71ee75a236f4d59eead05417988c`)
contains checker changes; B2 (`802510928a2f4baf1918b9e36c21c160ae3d5b14`) adds
directive construction changes. These are incremental results, separate from the
original study. Do not add or multiply percentages from the two studies.

## Direct code changes

| Change | Previous work | New behavior |
| --- | --- | --- |
| `SchemaTypeChecker.checkFieldTypesPresent` | Copied field types into a list and built nested argument-type lists before flattening them. | Visits the existing fields and arguments directly, in two passes that retain the existing error order. |
| `SchemaTypeChecker.checkNamedUniqueness` | Allocated a set for empty/single-item lists and performed separate membership and insertion lookups. | Returns immediately for fewer than two entries and uses the result of `Set.add` to identify duplicates. |
| `ImplementingTypesChecker` | Built a field set/map even when no interfaces were implemented; printed both argument-type ASTs to compare them. | Skips the field map after resolving an empty interface set, and compares type names/list/non-null wrappers directly. AST printing remains on error-reporting paths. |
| `SchemaTypeDirectivesChecker` | Rebuilt a directive's allowed-argument map for every use. | Reuses that map within one checker instance. Supplied values are still validated independently at each use. |
| `SchemaGeneratorHelper.BuildContext` | Scanned the runtime directive-definition set for each applied directive. | Maintains a per-build name index, preserving first-definition lookup behavior and the existing AST fallback for forward definitions. |
| `SchemaGeneratorAppliedDirectiveHelper` | Built two supplied-argument maps and four temporary argument lists/copies while constructing legacy and applied directives. | Inserts supplied arguments into the builders directly, shares one supplied-name set while adding missing/default arguments, and skips output-list allocation for empty directive lists. |

These helpers are on the SDL checking and construction paths of both generators.
`TypeDefinitionRegistry` is unchanged in this follow-up; profiles did not justify
an additional registry edit. The prior registry improvements are already in the baseline.

## Final three-fork comparison

Positive reduction means less time/allocation; negative means more. Time is ms/op.

| Fixture | Scope | Baseline ms/op | Candidate ms/op | Time reduction | Allocation reduction |
| --- | --- | ---: | ---: | ---: | ---: |
| 1 | SchemaGenerator generation | 2.735 | 2.272 | +16.94% | +25.48% |
| 1 | FastSchemaGenerator generation | 1.717 | 1.275 | +25.77% | +43.62% |
| 1 | SchemaGenerator parse + generation | 4.040 | 3.622 | +10.34% | +18.27% |
| 1 | FastSchemaGenerator parse + generation | 3.087 | 2.604 | +15.64% | +28.69% |
| 2 | SchemaGenerator generation | 42.931 | 31.493 | +26.64% | +37.93% |
| 2 | FastSchemaGenerator generation | 29.093 | 16.572 | +43.04% | +61.84% |
| 2 | SchemaGenerator parse + generation | 59.669 | 47.915 | +19.70% | +32.75% |
| 2 | FastSchemaGenerator parse + generation | 46.040 | 34.001 | +26.15% | +45.62% |
| 3 | SchemaGenerator generation | 122.737 | 119.655 | +2.51% | +6.37% |
| 3 | FastSchemaGenerator generation | 73.937 | 68.631 | +7.18% | +13.40% |
| 3 | SchemaGenerator parse + generation | 186.753 | 187.189 | -0.23% | +2.86% |
| 3 | FastSchemaGenerator parse + generation | 137.525 | 133.196 | +3.15% | +8.27% |
| 4 | SchemaGenerator generation | 1539.894 | 1507.842 | +2.08% | +4.45% |
| 4 | FastSchemaGenerator generation | 867.287 | 795.741 | +8.25% | +13.78% |
| 4 | SchemaGenerator parse + generation | 2017.339 | 1958.017 | +2.94% | +4.12% |
| 4 | FastSchemaGenerator parse + generation | 1333.891 | 1273.957 | +4.49% | +6.44% |
| 5 | SchemaGenerator generation | 7088.679 | 6840.991 | +3.49% | +2.88% |
| 5 | FastSchemaGenerator generation | 3405.253 | 3247.720 | +4.63% | +6.44% |
| 5 | SchemaGenerator parse + generation | 7903.000 | 7809.971 | +1.18% | +1.62% |
| 5 | FastSchemaGenerator parse + generation | 4240.320 | 4109.960 | +3.07% | +3.76% |

[Raw baseline](final/baseline-results.json), [raw B2](final/optimized-results.json), [fork means and warmup sequences](final/review.json).

## Variability and benchmark health

The final data contains all three forks per version/case and all five measurement
iterations per fork. No sample was removed or selectively replaced. Nineteen
time means and all twenty allocation means are lower. SchemaGenerator parsing
fixture 3 is 186.753 -> 187.189 ms/op (+0.23% time) with 2.86% less allocation;
its fork means and JMH 99.9% intervals overlap, so no time improvement is claimed.
The intervals also overlap for SchemaGenerator generation and parsing on fixture
4, and all four fixture 5 timing cases. These are observed mean differences,
not separately established significant gains. Fifteen iteration samples from
three JVMs are not fifteen independent JVM runs.

All 120 forks completed with zero allocation stalls. Last-five warmup CV or
absolute fitted drift above 5% flags a fork for inspection. Two candidate forks
were flagged: SchemaGenerator generation fixture 4, fork 3 (+5.51% fitted drift,
2.41% CV), and SchemaGenerator parsing fixture 5, fork 3 (+7.49% drift, 3.22% CV).
The former measured around its late-warmup level; the latter oscillated rather
than continuing the rise. Both have stable within-fork allocation. Both are
retained, with their full warmup and measurement sequences in
[health-review.json](health-review.json). They limit confidence in those small
time differences. All 40 environment snapshots show the same JVM/GC settings,
performance governors, healthy RAID arrays and no swap usage. Original CPU
governors and update timer/service states were restored and verified after the
last benchmark; see [host-restored-state.json](host-restored-state.json).

One machine, one JVM/collector configuration and five mocked-wiring schemas do
not establish performance for custom wiring, other collectors, other JVM versions
or other schemas. Allocation is JMH `gc.alloc.rate.norm` (bytes/op), not peak heap.

## Independent exploratory comparisons and profiles

A was measured against the follow-up baseline; B2 (including A) was measured
separately against A. Each exploratory comparison covers all 20 cases with one
fork/version/case. The [checker table](exploration/checker/comparison.md) has
18/20 lower time means (-4.76% to +42.96% reduction) and 16/20 lower allocation
means. The [directive table](exploration/directives/comparison.md) has 16/20 lower
time means (-2.12% to +4.93%) and 16/20 lower allocation means; all ten Fast
generator time means are lower. These attribute exploratory differences to each
group, not to every individual edit. Their raw JSON and warmup reviews are linked
beside the tables and are excluded from the final gain calculation.

Unfavorable results are preserved. A initially made SchemaGenerator fixture 5
generation/parsing 1.96%/4.76% slower. B2 versus A had higher SchemaGenerator
fixture 4 generation time/allocation (1.18%/4.96%) and fixture 5 parsing
time/allocation (2.12%/5.29%). Separate profiles and repeated forks investigated
these results. The same immutable A jar varied about 1.7% in fixture 5 generation
time/allocation between the two explorations. Fixture 4 profiles sampled existing
traversal singleton maps/sets/callbacks in both A and B2, though they were absent
from the baseline recording. This suggests fork-dependent JVM allocation
elimination, but does not prove the cause. The final combined B2 comparison has
lower time/allocation means in these cases; it does not independently disprove
every unfavorable A-versus-B2 difference. Keep the exploratory caveats when
interpreting the directive group's small incremental gains.

The [19 separate CPU/allocation recordings](profiles/) corroborate the targeted
paths. For Fast generation fixture 4, inclusive checker CPU/allocation shares
fall from 12.79%/18.40% to 9.10%/10.85%; applied-directive construction falls from
7.09%/13.42% to 4.47%/11.38%. Shares overlap and include GC work; they are not
measured time savings. All profile warmup screens passed and no allocation stalls
were recorded. Profile timings are excluded. [Allocation-shape observations](allocation-shape-review.json)
retain the diagnostic evidence; zero sampled weight does not establish zero cost.

## Correctness

The final production code passed 529 targeted IDL cases and the full Java 25
suite with ZGC and compact headers: 6,146 passed, seven existing skips, zero
failures/errors. The full local count includes two unrelated untracked parser
tests excluded from the PR. Javadoc and the per-class coverage gate passed, with
zero coverage regressions. The existing TestNG suite passed 141 cases with 49
expected skips before B2's storage cleanup and two additional malformed-interface
tests. All ten generated-schema hashes/type counts match baseline, A and B2.

Added Spock coverage includes error order/messages/locations, nested interface
argument wrappers, extension-introduced interfaces, malformed implementations,
repeated directive uses, default/unset/explicit-null values, both generators,
AST capture options, applied-only mode, comparator order, forward definitions and
isolation between builds. No new dependency is introduced. `zgc-tests.gradle`
configures Gradle's test JVMs; `SchemaInventory.java` reproduces the ten output
checks against frozen jars. No production edits occurred after the measured B2
revision and its full correctness checks.

## Reproduce

Build the baseline and B2 revisions in separate checkouts with Java 25 and
`./gradlew jmhJar`, then preserve the jars at stable paths. Jar hashes identify
the exact measured artifacts; fresh builds can differ because of archive metadata.
Fixture 5 concatenates its `.part1` and `.part2` resources in order. Source, jar,
fixture and printed-schema hashes are in [methodology.json](methodology.json)
and the inventory TSV files.

On an otherwise idle Linux host with enough memory, run:

```sh
python3 reproduce.py \
  --baseline /path/to/baseline.jar \
  --optimized /path/to/b2.jar \
  --output /path/to/new-results-directory \
  --lock /path/to/global-benchmark.lock \
  --java /path/to/jdk-25/bin/java
python3 analyze-comparison.py /path/to/new-results-directory
```

The default is three forks/version/case. To repeat the exploratory comparisons,
pass `--forks 1` with baseline/A or A/B2 jars. Every fork uses one benchmark
thread, 10 x 10-second warmup and 5 x 10-second timing, Java 25, a fixed 24 GiB
heap, ZGC, compact headers, adaptive GC workers and pre-touching. Iterations
finish their current operation, so the largest fixtures run longer than ten
seconds. A/B order alternates by method and reverses for each fixture; all three
forks of a version run together before the paired version. Results cover both
generators and both scopes for all five fixtures. Schema validation is enabled;
wiring is mocked. File I/O is outside the timed operation, and generation-only
reuses a registry parsed during setup.

Every benchmark, profile, conversion, build, test and large transfer on the host
must honor the same exclusive lock. The runner refuses existing output directories
and does not change host settings. Record host state separately and restore any
settings changed for measurement. Separate async-profiler 4.5 recordings used
the same warmup and 3 x 10-second recorded measurement iterations with CPU and
`alloc=1m` sampling. Follow the [original profiling instructions](../README.md)
with the follow-up jars; do not overlap profiling/conversion and timing.

The published final directory includes raw aggregate JMH JSON, all 40 timing logs,
all 120 GC logs, the manifest and derived review. Run
`python3 analyze-comparison.py final` to regenerate its table and review. Full
exploratory logs, environment snapshots, JFR/collapsed stacks and immutable jars
are also preserved in the working experiment archive and on the benchmark host.
All G1, 8 GiB, smoke, profiler and original-study timings are excluded from these
final speedup calculations.
