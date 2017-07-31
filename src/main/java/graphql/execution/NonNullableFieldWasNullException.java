package graphql.execution;

import graphql.ErrorType;
import graphql.GraphQLError;
import graphql.language.SourceLocation;

import java.util.List;

import static graphql.Assert.assertNotNull;

/**
 * See (http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability), but if a non nullable field
 * actually resolves to a null value and the parent type is nullable then the parent must in fact become null
 * so we use exceptions to indicate this special case
 */
public class NonNullableFieldWasNullException extends RuntimeException implements GraphQLError {

    private final ExecutionTypeInfo typeInfo;
    private final ExecutionPath path;


    public NonNullableFieldWasNullException(ExecutionTypeInfo typeInfo, ExecutionPath path) {
        super(buildMsg(assertNotNull(typeInfo), assertNotNull(path)));
        this.typeInfo = typeInfo;
        this.path = path;
    }

    private static String buildMsg(ExecutionTypeInfo typeInfo, ExecutionPath path) {
        if (typeInfo.hasParentType()) {
            return String.format("Cannot return null for non-nullable type: '%s' within parent '%s' (%s)", typeInfo.getType().getName(), typeInfo.getParentTypeInfo().getType().getName(), path);
        }
        return String.format("Cannot return null for non-nullable type: '%s' (%s) ", typeInfo.getType().getName(), path);
    }

    public ExecutionTypeInfo getTypeInfo() {
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

    /**
     * The graphql spec says that that path field of any error should be a list
     * of path entries - http://facebook.github.io/graphql/#sec-Errors
     *
     * @return the path in list format
     */
    public List<Object> getPath() {
        return path.toList();
    }


    @Override
    public String toString() {
        return "NonNullableFieldWasNullException{" +
                "path=" + path +
                "typeInfo=" + typeInfo +
                '}';
    }
}
