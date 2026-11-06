#!/usr/bin/env python3
"""Summarize diagnostic profiles without publishing their timing scores."""
import argparse
from collections import Counter
import json
from pathlib import Path
import re
import statistics

TARGETS = [
    'graphql/schema/idl/SchemaTypeChecker.checkTypeRegistry',
    'graphql/schema/idl/SchemaTypeChecker.checkFieldTypesPresent',
    'graphql/schema/idl/SchemaTypeChecker.checkNamedUniqueness',
    'graphql/schema/idl/ImplementingTypesChecker.checkInterfaceIsImplemented',
    'graphql/schema/idl/ImplementingTypesChecker.checkArgumentConsistency',
    'graphql/schema/idl/ImplementingTypesChecker.sameType',
    'graphql/language/AstPrinter.printAstCompact',
    'graphql/schema/idl/SchemaTypeDirectivesChecker.checkDirectiveArguments',
    'graphql/schema/idl/SchemaGeneratorHelper.buildOutputType',
    'graphql/schema/idl/SchemaGeneratorAppliedDirectiveHelper.buildAppliedDirectives',
    'graphql/schema/idl/SchemaGeneratorAppliedDirectiveHelper.buildAppliedDirective',
    'graphql/schema/idl/SchemaGeneratorAppliedDirectiveHelper.transferMissingArguments',
    'graphql/schema/idl/SchemaGeneratorAppliedDirectiveHelper.addMissingArguments',
    'graphql/schema/GraphQLSchema$Builder.buildImpl',
    'graphql/schema/SchemaTraverser.doTraverse',
    'graphql/schema/SchemaElementChildrenContainer.getChildrenAsList',
    'graphql/util/DefaultTraverserContext.isVisited',
    'graphql/util/TraverserState.addVisited',
]


def summarize(path):
    total = 0
    inclusive = Counter()
    leaves = Counter()
    with path.open() as source:
        for line in source:
            stack, separator, weight_text = line.rstrip().rpartition(' ')
            assert separator and weight_text.isdigit(), path
            weight = int(weight_text)
            frames = [re.sub(r'(?:_\[[a-z0-9]+\])+$', '', frame) for frame in stack.split(';')]
            total += weight
            leaves[frames[-1]] += weight
            for frame in set(frames):
                inclusive[frame] += weight
    assert total > 0, path
    return {
        'total_weight': total,
        'inclusive_targets': {
            frame: {'weight': inclusive[frame], 'share_pct': 100 * inclusive[frame] / total}
            for frame in TARGETS
        },
        'top_leaves': [{'frame': frame, 'weight': weight, 'share_pct': 100 * weight / total}
                       for frame, weight in leaves.most_common(25)],
    }


def inspect_case(path):
    rows = json.loads((path / 'profile-timings-not-for-comparison.json').read_text())
    assert len(rows) == 1, path
    row = rows[0]
    assert row['forks'] == 1 and row['threads'] == 1
    assert row['warmupIterations'] == 10 and row['warmupTime'] == '10 s'
    assert row['measurementIterations'] == 3 and row['measurementTime'] == '10 s'
    assert row['jdkVersion'].startswith('25.')
    for flag in ['-Xms24g', '-Xmx24g', '-XX:+UseZGC', '-XX:+UseCompactObjectHeaders',
                 '-XX:+UseDynamicNumberOfGCThreads']:
        assert flag in row['jvmArgs'], path
    log = (path / 'profile.log').read_text()
    warmup = [float(value) for value in re.findall(r'# Warmup Iteration\s+\d+:\s+([\d.]+)\s+ms/op', log)]
    assert len(warmup) == 10, path
    tail = warmup[-5:]
    mean = statistics.mean(tail)
    cv = statistics.stdev(tail) / mean
    slope = sum((i - 2) * (value - mean) for i, value in enumerate(tail)) / 10
    drift = 4 * slope / mean
    stalls = [{'log': gc.name, 'line': line} for gc in path.glob('gc-*.log')
              for line in gc.read_text().splitlines() if 'Allocation Stall (' in line]
    return {
        'benchmark': row['benchmark'], 'fixture': row['params']['schemaFile'],
        'warmup': {'last_five_cv_pct': 100 * cv, 'last_five_fitted_drift_pct': 100 * drift,
                   'inspect': cv > .05 or abs(drift) > .05},
        'allocation_stalls': stalls,
        'cpu': summarize(path / 'cpu.collapsed'),
        'allocation': summarize(path / 'alloc.collapsed'),
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    args = parser.parse_args()
    root = args.directory
    progress = json.loads((root / 'progress.json').read_text())
    assert progress['state'] == 'complete', progress
    cases = [inspect_case(path) for path in sorted(root.glob('*-profile'))]
    assert len(cases) == progress['total'] == progress['completed']
    manifest = json.loads((root / 'manifest.json').read_text())
    review = {
        'revision': manifest['revision'], 'jar_sha256': manifest['jar_sha256'], 'cases': cases,
        'interpretation': 'Inclusive shares overlap. Profiles are separate diagnostic runs, not speedup evidence. '
                          'Zero weight means not sampled; it does not prove zero cost. Differences in shares do not '
                          'establish differences in absolute cost or explain timing regressions by themselves.',
    }
    (root / 'profile-review.json').write_text(json.dumps(review, indent=2) + '\n')
    print(json.dumps({'revision': review['revision'], 'cases': len(cases),
                      'warmup_flags': sum(case['warmup']['inspect'] for case in cases),
                      'allocation_stalls': sum(len(case['allocation_stalls']) for case in cases)}))


if __name__ == '__main__':
    main()
