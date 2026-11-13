#!/usr/bin/env python3
"""Validate paired JMH results, report all cases, and screen warmup/GC logs."""
import argparse
import json
from pathlib import Path
import re
import statistics

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('directory', type=Path)
args = parser.parse_args()
root = args.directory
manifest = json.loads((root / 'manifest.json').read_text())
baseline, candidate = list(manifest['jars'])
forks = manifest['forks']
methods = [
    'benchmark.BuildSchemaBenchmark.benchmarkBuildSchemaAvgTime',
    'benchmark.BuildSchemaBenchmark.benchmarkBuildSchemaAvgTimeFast',
    'benchmark.CreateSchemaBenchmark.benchmarkLargeSchemaCreateAvgTime',
    'benchmark.CreateSchemaBenchmark.benchmarkLargeSchemaCreateAvgTimeFast'
]
labels = ['SchemaGenerator generation', 'FastSchemaGenerator generation',
          'SchemaGenerator parse + generation', 'FastSchemaGenerator parse + generation']
expected = {(m, 'large-schema-' + str(n) + '.graphqls') for m in methods for n in range(1, 6)}
records = {}
for variant in [baseline, candidate]:
    rows = json.loads((root / (variant + '-results.json')).read_text())
    assert {(r['benchmark'], r['params']['schemaFile']) for r in rows} == expected
    assert len(rows) == 20
    for r in rows:
        assert r['forks'] == forks and r['threads'] == 1
        assert r['mode'] == 'avgt' and r['primaryMetric']['scoreUnit'] == 'ms/op'
        assert r['warmupIterations'] == 10 and r['warmupTime'] == '10 s'
        assert r['measurementIterations'] == 5 and r['measurementTime'] == '10 s'
        assert r['jdkVersion'].startswith('25.')
        for flag in ['-Xms24g', '-Xmx24g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders', '-XX:+UseDynamicNumberOfGCThreads']:
            assert flag in r['jvmArgs']
        assert len(r['primaryMetric']['rawData']) == forks
        assert all(len(f) == 5 for f in r['primaryMetric']['rawData'])
    records[variant] = {(r['benchmark'], r['params']['schemaFile']): r for r in rows}
comparisons = []
lines = [f'{baseline} vs {candidate}; {forks} fork(s) per version/case. Positive reductions are improvements.', '',
         '| Fixture | Scope | Baseline ms/op | Candidate ms/op | Time reduction | Allocation reduction |',
         '| --- | --- | ---: | ---: | ---: | ---: |']
for n in range(1, 6):
    for method, label in zip(methods, labels):
        key = method, f'large-schema-{n}.graphqls'
        a, b = records[baseline][key], records[candidate][key]
        at, bt = a['primaryMetric']['score'], b['primaryMetric']['score']
        aa, ba = a['secondaryMetrics']['gc.alloc.rate.norm']['score'], b['secondaryMetrics']['gc.alloc.rate.norm']['score']
        time_gain, allocation_gain = 100 * (1 - bt / at), 100 * (1 - ba / aa)
        row = {'fixture': n, 'scope': label, 'baseline_ms': at, 'candidate_ms': bt,
               'time_reduction_pct': time_gain, 'allocation_reduction_pct': allocation_gain,
               'baseline_bytes_per_op': aa, 'candidate_bytes_per_op': ba,
               'baseline_fork_means': [statistics.mean(f) for f in a['primaryMetric']['rawData']],
               'candidate_fork_means': [statistics.mean(f) for f in b['primaryMetric']['rawData']],
               'baseline_allocation_fork_means': [statistics.mean(f) for f in a['secondaryMetrics']['gc.alloc.rate.norm']['rawData']],
               'candidate_allocation_fork_means': [statistics.mean(f) for f in b['secondaryMetrics']['gc.alloc.rate.norm']['rawData']],
               'baseline_jmh_score_confidence': a['primaryMetric']['scoreConfidence'],
               'candidate_jmh_score_confidence': b['primaryMetric']['scoreConfidence']}
        comparisons.append(row)
        lines.append(f'| {n} | {label} | {at:.3f} | {bt:.3f} | {time_gain:+.2f}% | {allocation_gain:+.2f}% |')
warmup = []
for path in sorted(root.glob('*.log')):
    if path.name.startswith('gc-'):
        continue
    series = []
    current = []
    for line in path.read_text(errors='replace').splitlines():
        match = re.search(r'# Warmup Iteration\s+(\d+):\s+([\d.]+)\s+ms/op', line)
        if not match:
            continue
        if int(match[1]) == 1 and current:
            series.append(current)
            current = []
        current.append(float(match[2]))
    if current:
        series.append(current)
    if not series:
        continue
    assert len(series) == forks, path
    for number, values in enumerate(series, 1):
        assert len(values) == 10, path
        tail = values[-5:]
        mean = statistics.mean(tail)
        cv = statistics.stdev(tail) / mean
        slope = sum((i - 2) * (value - mean) for i, value in enumerate(tail)) / 10
        drift = slope * 4 / mean
        warmup.append({'log': path.name, 'fork': number, 'warmup_values': values,
                       'last_five_cv_pct': 100 * cv, 'last_five_fitted_drift_pct': 100 * drift,
                       'inspect': abs(drift) > .05 or cv > .05})
assert len(warmup) == 40 * forks
stalls = [{'log': p.name, 'line': line} for p in root.glob('gc-*.log')
          for line in p.read_text(errors='replace').splitlines() if 'Allocation Stall (' in line]
review = {'baseline': baseline, 'candidate': candidate, 'forks': forks, 'cases': comparisons,
          'warmup': warmup, 'allocation_stalls': stalls,
          'note': 'Warmup flags require review; do not discard or selectively replace observations. One-fork results are exploratory.'}
(root / 'review.json').write_text(json.dumps(review, indent=2) + '\n')
lines += ['', f'Warmup screens flagged: {sum(w["inspect"] for w in warmup)}/{len(warmup)}. Actual allocation stalls: {len(stalls)}.']
(root / 'comparison.md').write_text('\n'.join(lines) + '\n')
print('\n'.join(lines))
