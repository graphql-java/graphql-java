package graphql.performance.page.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class BenchmarkSeries {
    private final String seriesKey;
    private final String benchmarkName;
    private final String benchmarkClassName;
    private final String methodName;
    private final String mode;
    private final String paramsString;
    private final List<DataPoint> dataPoints = new ArrayList<>();

    public BenchmarkSeries(String seriesKey, String benchmarkName, String benchmarkClassName,
                           String methodName, String mode, String paramsString) {
        this.seriesKey = seriesKey;
        this.benchmarkName = benchmarkName;
        this.benchmarkClassName = benchmarkClassName;
        this.methodName = methodName;
        this.mode = mode;
        this.paramsString = paramsString;
    }

    public String getSeriesKey() {
        return seriesKey;
    }

    public String getBenchmarkName() {
        return benchmarkName;
    }

    public String getBenchmarkClassName() {
        return benchmarkClassName;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getMode() {
        return mode;
    }

    public String getParamsString() {
        return paramsString;
    }

    public List<DataPoint> getDataPoints() {
        return dataPoints;
    }

    public void addDataPoint(DataPoint dp) {
        dataPoints.add(dp);
    }

    /**
     * Returns a display label for the series, e.g. "benchMarkSimpleQueriesThroughput (howManyItems=5)"
     */
    public String getDisplayLabel() {
        if (paramsString.isEmpty()) {
            return methodName;
        }
        return methodName + " (" + paramsString + ")";
    }

    /**
     * Returns the score unit of the most recent data point (used as the normalized unit).
     */
    public String getScoreUnit() {
        if (dataPoints.isEmpty()) {
            return "";
        }
        return dataPoints.getLast().scoreUnit();
    }

    public static record DataPoint(
            Instant timestamp,
            String commitHash,
            String jdkVersion,
            double score,
            double scoreError,
            String scoreUnit
    ) {}
}
