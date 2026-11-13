package graphql.validation;

import graphql.Internal;
import org.jspecify.annotations.NullMarked;

/**
 * Exception thrown when depth, merged field count or field occurrence limits are exceeded during validation.
 * This exception is caught by the Validator and converted to a ValidationError.
 */
@Internal
@NullMarked
public class QueryComplexityLimitsExceeded extends RuntimeException {

    private final ValidationErrorType errorType;
    private final int limit;
    private final long actual;

    public QueryComplexityLimitsExceeded(ValidationErrorType errorType, int limit, long actual) {
        super(errorType.name() + ": limit=" + limit + ", actual=" + actual);
        this.errorType = errorType;
        this.limit = limit;
        this.actual = actual;
    }

    public ValidationErrorType getErrorType() {
        return errorType;
    }

    public int getLimit() {
        return limit;
    }

    public long getActual() {
        return actual;
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // No stack trace for performance - this is a control flow exception
        return this;
    }
}
