package graphql.performance.page.analyzer;

import graphql.performance.page.model.BenchmarkResult;
import graphql.performance.page.model.BenchmarkSeries;
import graphql.performance.page.model.ResultFile;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BenchmarkAnalyzer {

    /**
     * Unit conversion factors to a common base unit.
     * For time units: base is nanoseconds.
     * For throughput units: base is ops/s.
     */
    private static final Map<String, Double> TIME_UNIT_TO_NANOS = Map.of(
            "ns/op", 1.0,
            "us/op", 1_000.0,
            "ms/op", 1_000_000.0,
            "s/op", 1_000_000_000.0
    );

    private static final Map<String, Double> THROUGHPUT_UNIT_TO_OPS_PER_S = Map.of(
            "ops/s", 1.0,
            "ops/ms", 1_000.0,
            "ops/us", 1_000_000.0,
            "ops/ns", 1_000_000_000.0
    );

    /**
     * Groups all results from all files into benchmark series, sorted by timestamp.
     * Returns a map of benchmarkClassName -> list of series for that class.
     */
    public Map<String, List<BenchmarkSeries>> analyze(List<ResultFile> files) {
        // Sort files by timestamp
        files.sort(Comparator.comparing(ResultFile::getTimestamp));

        // Build series map: seriesKey -> BenchmarkSeries
        Map<String, BenchmarkSeries> seriesMap = new LinkedHashMap<>();

        for (ResultFile file : files) {
            for (BenchmarkResult result : file.getResults()) {
                String key = result.getSeriesKey();
                BenchmarkSeries series = seriesMap.computeIfAbsent(key, k ->
                        new BenchmarkSeries(
                                key,
                                result.getBenchmark(),
                                result.getBenchmarkClassName(),
                                result.getBenchmarkMethodName(),
                                result.getMode(),
                                result.getParamsString()
                        )
                );
                series.addDataPoint(new BenchmarkSeries.DataPoint(
                        file.getTimestamp(),
                        file.getCommitHash(),
                        file.getJdkVersion(),
                        result.getPrimaryMetric().getScore(),
                        result.getPrimaryMetric().getScoreError(),
                        result.getPrimaryMetric().getScoreUnit()
                ));
            }
        }

        // Normalize units within each series
        for (BenchmarkSeries series : seriesMap.values()) {
            normalizeUnits(series);
        }

        // Group by benchmark class name, sorted alphabetically
        Map<String, List<BenchmarkSeries>> grouped = new TreeMap<>();
        for (BenchmarkSeries series : seriesMap.values()) {
            grouped.computeIfAbsent(series.getBenchmarkClassName(), k -> new java.util.ArrayList<>())
                    .add(series);
        }

        // Sort series within each class: by mode (thrpt first), then by display label
        for (List<BenchmarkSeries> seriesList : grouped.values()) {
            seriesList.sort(Comparator
                    .comparing(BenchmarkSeries::getMode)
                    .thenComparing(BenchmarkSeries::getDisplayLabel));
        }

        return grouped;
    }

    /**
     * Normalizes all data points in a series to the most recent unit.
     * This handles cases where a benchmark changed units over time (e.g. ns/op -> ms/op).
     */
    private void normalizeUnits(BenchmarkSeries series) {
        List<BenchmarkSeries.DataPoint> points = series.getDataPoints();
        if (points.size() < 2) {
            return;
        }

        // Target unit is the most recent data point's unit
        String targetUnit = points.getLast().scoreUnit();

        // Check if all points already have the same unit
        boolean allSame = points.stream().allMatch(dp -> dp.scoreUnit().equals(targetUnit));
        if (allSame) {
            return;
        }

        // Determine if these are time or throughput units
        boolean isTime = TIME_UNIT_TO_NANOS.containsKey(targetUnit);
        boolean isThroughput = THROUGHPUT_UNIT_TO_OPS_PER_S.containsKey(targetUnit);

        if (!isTime && !isThroughput) {
            // Unknown unit family, skip normalization
            return;
        }

        // Replace data points with normalized values
        for (int i = 0; i < points.size(); i++) {
            BenchmarkSeries.DataPoint dp = points.get(i);
            if (!dp.scoreUnit().equals(targetUnit)) {
                double factor = computeConversionFactor(dp.scoreUnit(), targetUnit, isTime);
                if (!Double.isNaN(factor)) {
                    points.set(i, new BenchmarkSeries.DataPoint(
                            dp.timestamp(),
                            dp.commitHash(),
                            dp.jdkVersion(),
                            dp.score() * factor,
                            dp.scoreError() * factor,
                            targetUnit
                    ));
                }
            }
        }
    }

    private double computeConversionFactor(String fromUnit, String toUnit, boolean isTime) {
        if (isTime) {
            Double fromFactor = TIME_UNIT_TO_NANOS.get(fromUnit);
            Double toFactor = TIME_UNIT_TO_NANOS.get(toUnit);
            if (fromFactor == null || toFactor == null) {
                return Double.NaN;
            }
            return fromFactor / toFactor;
        } else {
            Double fromFactor = THROUGHPUT_UNIT_TO_OPS_PER_S.get(fromUnit);
            Double toFactor = THROUGHPUT_UNIT_TO_OPS_PER_S.get(toUnit);
            if (fromFactor == null || toFactor == null) {
                return Double.NaN;
            }
            return fromFactor / toFactor;
        }
    }
}
