package graphql;

import java.util.Collections;
import java.util.List;

import graphql.language.SourceLocation;

/**
 * The graphql spec says that subscriptions and mutations are optional but it does not specify how to respond
 * when either or both are not supported. This error is returned in this case.
 *
 * http://facebook.github.io/graphql/#sec-Initial-types
 */
public class OperationNotSupportedError implements GraphQLError {

    private List<SourceLocation> sourceLocations;
    private String message;

    public OperationNotSupportedError(String message, SourceLocation sourceLocation) {
        this.message = message;
        this.sourceLocations = sourceLocation == null ? null : Collections.singletonList(sourceLocation);
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return sourceLocations;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.OperationNotSupported;
    }

    @Override
    public String toString() {
        return "OperationNotSupportedError{" +
            "sourceLocations=" + sourceLocations +
            ", message='" + message + '\'' +
            '}';
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object o) {
        return GraphqlErrorHelper.equals(this, o);
    }

    @Override
    public int hashCode() {
        return GraphqlErrorHelper.hashCode(this);
    }
}
