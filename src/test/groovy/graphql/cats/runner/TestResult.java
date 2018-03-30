package graphql.cats.runner;

public class TestResult {
    private final String testName;
    private final String reason;
    private final String query;
    private final boolean passed;
    private final Throwable throwable;

    private TestResult(String testName, String reason, String query, boolean passed, Throwable throwable) {
        this.testName = testName;
        this.reason = reason;
        this.query = query;
        this.passed = passed;
        this.throwable = throwable;
    }

    public static TestResult passed(String testName, String query) {
        return new TestResult(testName, "success", query, true, null);
    }

    public static TestResult failed(String testName, String query, String reason) {
        return new TestResult(testName, reason, query, false, null);
    }

    public static TestResult failed(String testName, String query, String reason, Throwable throwable) {
        return new TestResult(testName, reason, query, false, throwable);
    }

    public String getTestName() {
        return testName;
    }

    public boolean isPassed() {
        return passed;
    }

    public String getReason() {
        return reason;
    }

    public String getQuery() {
        return query;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
