package graphql.execution;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.List;

/**
 * See (http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability), but if a non nullable field
 * actually resolves to a null value and the parent type is nullable then the parent must in fact become null
 * so we use exceptions to indicate this special case
 */
public class NonNullableFieldWasNullException extends RuntimeException implements GraphQLError {

    private final TypeInfo typeInfo;


    public NonNullableFieldWasNullException(TypeInfo typeInfo) {
        super(buildMsg(typeInfo));
        this.typeInfo = typeInfo;
    }

    private static String buildMsg(TypeInfo typeInfo) {
        if (typeInfo.hasParentType()) {
            return String.format("Cannot return null for non-nullable type: '%s' within parent '%s'", typeInfo.type().getName(), typeInfo.parentTypeInfo().type().getName());
        }
        return String.format("Cannot return null for non-nullable type: '%s' ", typeInfo.type().getName());
    }

    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    @Override
    public List<SourceLocation> getLocations() {
        return null;
    }

    @Override
    public ErrorType getErrorType() {
        return null;
    }
}
