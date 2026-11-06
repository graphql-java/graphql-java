#!/usr/bin/env python3
"""Repeat the serial, counterbalanced three-fork schema-generation comparison."""
import argparse
import datetime
import fcntl
import hashlib
import json
from pathlib import Path
import subprocess

METHODS = [
    'benchmark.BuildSchemaBenchmark.benchmarkBuildSchemaAvgTime',
    'benchmark.BuildSchemaBenchmark.benchmarkBuildSchemaAvgTimeFast',
    'benchmark.CreateSchemaBenchmark.benchmarkLargeSchemaCreateAvgTime',
    'benchmark.CreateSchemaBenchmark.benchmarkLargeSchemaCreateAvgTimeFast',
]
JVM = ('-Xms24g -Xmx24g -XX:+UseZGC -XX:+UseCompactObjectHeaders '
       '-XX:+UseDynamicNumberOfGCThreads -XX:+AlwaysPreTouch '
       '-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints '
       '-Xlog:gc:file=gc-%p.log:uptime,level,tags')


def utc():
    return datetime.datetime.now(datetime.timezone.utc).isoformat()


def run_case(java, jar, variant, method, schema, destination):
    name = f'{variant}-{method.split(".")[-1]}-{schema}'
    output = destination / (name + '.json')
    command = [java, '-jar', str(jar), '^' + method.replace('.', '\\.') + '$',
               '-p', f'schemaFile=large-schema-{schema}.graphqls',
               '-f', '3', '-t', '1', '-wi', '10', '-w', '10s', '-i', '5', '-r', '10s',
               '-tu', 'ms', '-bm', 'avgt', '-foe', 'true', '-jvmArgs', JVM,
               '-prof', 'gc', '-rf', 'json', '-rff', str(output)]
    with (destination / (name + '.log')).open('w') as log:
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True, cwd=destination)
    result = json.loads(output.read_text())
    if len(result) != 1:
        raise RuntimeError('Expected exactly one completed benchmark')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', required=True, type=Path)
    parser.add_argument('--optimized', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path, help='New output directory; existing directories are refused')
    parser.add_argument('--lock', required=True, type=Path, help='Same host-wide lock file used by every performance workload')
    parser.add_argument('--java', default='java', help='Java 25 executable; defaults to PATH')
    args = parser.parse_args()
    jars = {'baseline': args.baseline.resolve(strict=True), 'optimized': args.optimized.resolve(strict=True)}
    destination = args.output.resolve()
    with args.lock.open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        destination.mkdir()
        version = subprocess.run([args.java, '-version'], capture_output=True, text=True, check=True)
        version_text = version.stdout + version.stderr
        if 'version "25.' not in version_text:
            raise SystemExit('Java 25 is required')
        manifest = {'started': utc(), 'java': version_text, 'jvm_arguments': JVM,
                    'jars': {name: hashlib.sha256(path.read_bytes()).hexdigest() for name, path in jars.items()},
                    'ordering': 'Alternate A/B and B/A by method and reverse for each successive schema'}
        (destination / 'manifest.json').write_text(json.dumps(manifest, indent=2) + '\n')
        run_matrix(args.java, jars, destination)


def run_matrix(java, jars, destination):
    results = {variant: [] for variant in jars}
    cases = [(schema, index, method) for schema in range(1, 6) for index, method in enumerate(METHODS)]
    for schema, index, method in cases:
        order = ['baseline', 'optimized'] if (index + schema - 1) % 2 == 0 else ['optimized', 'baseline']
        for variant in order:
            progress = {'updated': utc(), 'state': 'measuring', 'variant': variant,
                        'schema': schema, 'method': method,
                        'completed': sum(map(len, results.values())), 'total': 40}
            (destination / 'progress.json').write_text(json.dumps(progress, indent=2) + '\n')
            results[variant] += run_case(java, jars[variant], variant, method, schema, destination)
            (destination / (variant + '-results.json')).write_text(json.dumps(results[variant], indent=2) + '\n')
    (destination / 'progress.json').write_text(json.dumps({'state': 'complete', 'completed': 40,
                                                         'total': 40, 'finished': utc()}, indent=2) + '\n')


if __name__ == '__main__':
    main()
