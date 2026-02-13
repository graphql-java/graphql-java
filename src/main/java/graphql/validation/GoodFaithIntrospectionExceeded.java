package graphql.validation;

import graphql.Internal;
import graphql.introspection.GoodFaithIntrospection;
import org.jspecify.annotations.NullMarked;

/**
 * Exception thrown when a good-faith introspection check fails during validation.
 * This exception is NOT caught by the Validator — it propagates up to GraphQL.parseAndValidate()
 * where it is converted to a {@link GoodFaithIntrospection.BadFaithIntrospectionError}.
 */
@Internal
@NullMarked
public class GoodFaithIntrospectionExceeded extends RuntimeException {

    private final boolean tooBig;
    private final String detail;

    private GoodFaithIntrospectionExceeded(boolean tooBig, String detail) {
        super(detail);
        this.tooBig = tooBig;
        this.detail = detail;
    }

    public static GoodFaithIntrospectionExceeded tooManyFields(String fieldCoordinate) {
        return new GoodFaithIntrospectionExceeded(false, fieldCoordinate);
    }

    public static GoodFaithIntrospectionExceeded tooBigOperation(String message) {
        return new GoodFaithIntrospectionExceeded(true, message);
    }

    public GoodFaithIntrospection.BadFaithIntrospectionError toBadFaithError() {
        if (tooBig) {
            return GoodFaithIntrospection.BadFaithIntrospectionError.tooBigOperation(detail);
        }
        return GoodFaithIntrospection.BadFaithIntrospectionError.tooManyFields(detail);
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        // No stack trace for performance - this is a control flow exception
        return this;
    }
}
