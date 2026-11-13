package graphql.execution;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.GraphQLException;
import graphql.PublicApi;
import graphql.language.SourceLocation;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * https://facebook.github.io/graphql/#sec-Input-Objects
 *
 * - This unordered map should not contain any entries with names not defined by a field of this input object type, otherwise an error should be thrown.
 */
@PublicApi
@NullMarked
public class InputMapDefinesTooManyFieldsException extends GraphQLException implements GraphQLError {

    public InputMapDefinesTooManyFieldsException(GraphQLType graphQLType, String fieldName) {
        super(String.format("The variables input contains a field name '%s' that is not defined for input object type '%s' ", fieldName, GraphQLTypeUtil.simplePrint(graphQLType)));
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
