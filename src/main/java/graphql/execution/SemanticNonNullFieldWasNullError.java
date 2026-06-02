package graphql.execution;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphqlErrorHelper;
import graphql.Internal;
import graphql.language.SourceLocation;

import java.util.List;

import static java.lang.String.format;

/**
 * This error is synthesized when a position annotated with the {@code @semanticNonNull} directive resolves to null
 * without a matching error already being present in the {@code errors} array.
 *
 * See <a href="https://specs.apollo.dev/nullability/v0.4/">the Apollo nullability specification</a>
 */
@Internal
public class SemanticNonNullFieldWasNullError implements GraphQLError {

    private final String message;
    private final List<Object> path;

    public SemanticNonNullFieldWasNullError(ExecutionStepInfo executionStepInfo, ResultPath path) {
        this.message = format("The field at path '%s' was declared as semantically non null via the @semanticNonNull directive,"
                + " but the code involved in retrieving data has returned a null value with no matching error."
                + " The semantically non-null type is '%s'.", path, executionStepInfo.getUnwrappedNonNullType());
        this.path = path.toList();
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public List<Object> getPath() {
        return path;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.NullValueInNonNullableField;
    }

    @Override
    public String toString() {
        return "SemanticNonNullError{" +
                "message='" + message + '\'' +
                ", path=" + path +
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
