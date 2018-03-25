package graphql.cats.runner;

public class TestResult {
    private final String testName;
    private final String reason;
    private final boolean passed;
    private final Throwable throwable;

    private TestResult(String testName, String reason, boolean passed, Throwable throwable) {
        this.testName = testName;
        this.reason = reason;
        this.passed = passed;
        this.throwable = throwable;
    }

    public static TestResult passed(String testName) {
        return new TestResult(testName, "success", true, null);
    }

    public static TestResult failed(String testName, String reason) {
        return new TestResult(testName, reason, false, null);
    }

    public static TestResult failed(String testName, String reason, Throwable throwable) {
        return new TestResult(testName, reason, false, throwable);
    }

    public String getTestName() {
        return testName;
    }

    public boolean isPassed() {
        return passed;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
