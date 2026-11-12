package graphql.introspection;

import graphql.ErrorClassification;
import graphql.GraphQLError;
import graphql.PublicApi;
import graphql.language.SourceLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Error returned when an operation exceeds the introspection protections configured through
 * {@link graphql.validation.QueryComplexityLimits}. Its classification is {@code BadFaithIntrospection}.
 */
@PublicApi
@NullMarked
public class BadFaithIntrospectionError implements GraphQLError {
    private final String message;

    /**
     * @param fieldCoordinate the restricted coordinate selected more than once
     * @return an error identifying the repeated coordinate
     */
    public static BadFaithIntrospectionError tooManyFields(String fieldCoordinate) {
        return new BadFaithIntrospectionError(String.format("This request is not asking for introspection in good faith - %s is present too often!", fieldCoordinate));
    }

    /**
     * @param message the exceeded merged-field or depth limit
     * @return an error describing the exceeded introspection limit
     */
    public static BadFaithIntrospectionError tooBigOperation(String message) {
        return new BadFaithIntrospectionError(String.format("This request is not asking for introspection in good faith - the query is too big: %s", message));
    }

    private BadFaithIntrospectionError(String message) {
        this.message = message;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public ErrorClassification getErrorType() {
        return ErrorClassification.errorClassification("BadFaithIntrospection");
    }

    @Override
    public @Nullable List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public String toString() {
        return "BadFaithIntrospectionError{" +
                "message='" + message + '\'' +
                '}';
    }
}
