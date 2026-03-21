package graphql.performance.page.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BenchmarkResult {
    private String benchmark;
    private String mode;
    private PrimaryMetric primaryMetric;
    private Map<String, String> params;

    public String getBenchmark() {
        return benchmark;
    }

    public void setBenchmark(String benchmark) {
        this.benchmark = benchmark;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public PrimaryMetric getPrimaryMetric() {
        return primaryMetric;
    }

    public void setPrimaryMetric(PrimaryMetric primaryMetric) {
        this.primaryMetric = primaryMetric;
    }

    public Map<String, String> getParams() {
        return params;
    }

    public void setParams(Map<String, String> params) {
        this.params = params;
    }

    /**
     * Returns the simple class name from the fully qualified benchmark name.
     * e.g. "performance.ComplexQueryPerformance.benchMarkSimpleQueriesThroughput" -> "ComplexQueryPerformance"
     */
    public String getBenchmarkClassName() {
        String[] parts = benchmark.split("\\.");
        return parts.length >= 2 ? parts[parts.length - 2] : benchmark;
    }

    /**
     * Returns the method name from the fully qualified benchmark name.
     * e.g. "performance.ComplexQueryPerformance.benchMarkSimpleQueriesThroughput" -> "benchMarkSimpleQueriesThroughput"
     */
    public String getBenchmarkMethodName() {
        String[] parts = benchmark.split("\\.");
        return parts.length >= 1 ? parts[parts.length - 1] : benchmark;
    }

    /**
     * Returns a params string for display, e.g. "howManyItems=5" or "" if no params.
     */
    public String getParamsString() {
        if (params == null || params.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        params.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            sb.append(e.getKey()).append("=").append(e.getValue());
        });
        return sb.toString();
    }

    /**
     * Returns a unique series key: benchmark + mode + sorted params.
     */
    public String getSeriesKey() {
        String key = benchmark + ":" + mode;
        String p = getParamsString();
        if (!p.isEmpty()) {
            key += ":" + p;
        }
        return key;
    }
}
