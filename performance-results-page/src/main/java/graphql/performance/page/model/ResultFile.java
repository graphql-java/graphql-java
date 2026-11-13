package graphql.performance.page.model;

import java.time.Instant;
import java.util.List;

public class ResultFile {
    private final Instant timestamp;
    private final String commitHash;
    private final String jdkVersion;
    private final List<BenchmarkResult> results;

    public ResultFile(Instant timestamp, String commitHash, String jdkVersion, List<BenchmarkResult> results) {
        this.timestamp = timestamp;
        this.commitHash = commitHash;
        this.jdkVersion = jdkVersion;
        this.results = results;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public String getCommitHash() {
        return commitHash;
    }

    public String getJdkVersion() {
        return jdkVersion;
    }

    public List<BenchmarkResult> getResults() {
        return results;
    }
}
