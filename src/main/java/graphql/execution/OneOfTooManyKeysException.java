package graphql.execution;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.SourceLocation;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The input map to One Of Input Types MUST only have 1 entry
 */
@PublicApi
@NullMarked
public class OneOfTooManyKeysException extends GraphQLException implements GraphQLError {

    public OneOfTooManyKeysException(String message) {
        super(message);
    }

    @Override
    public @Nullable List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType getErrorType() {
        return ErrorType.ValidationError;
    }
}
