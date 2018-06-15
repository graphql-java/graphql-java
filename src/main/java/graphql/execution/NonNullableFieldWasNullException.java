package graphql.execution;

import graphql.GraphQLExecutionException;

import static graphql.Assert.assertNotNull;

/**
 * See (http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability), but if a non nullable field
 * actually resolves to a null value and the parent type is nullable then the parent must in fact become null
 * so we use exceptions to indicate this special case
 */
public class NonNullableFieldWasNullException extends GraphQLExecutionException {

    private final ExecutionTypeInfo typeInfo;
    private final ExecutionPath path;


    public NonNullableFieldWasNullException(ExecutionTypeInfo typeInfo, ExecutionPath path) {
        super(
                mkMessage(assertNotNull(typeInfo),
                        assertNotNull(path))
        );
        this.typeInfo = typeInfo;
        this.path = path;
    }

    public NonNullableFieldWasNullException(NonNullableFieldWasNullException previousException) {
        super(
                mkMessage(
                        assertNotNull(previousException.typeInfo.getParentTypeInfo()),
                        assertNotNull(previousException.typeInfo.getParentTypeInfo().getPath())
                ),
                previousException
        );
        this.typeInfo = previousException.typeInfo.getParentTypeInfo();
        this.path = previousException.typeInfo.getParentTypeInfo().getPath();
    }


    private static String mkMessage(ExecutionTypeInfo typeInfo, ExecutionPath path) {
        if (typeInfo.hasParentType()) {
            return String.format("Cannot return null for non-nullable type: '%s' within parent '%s' (%s)", typeInfo.getType().getName(), typeInfo.getParentTypeInfo().getType().getName(), path);
        }
        return String.format("Cannot return null for non-nullable type: '%s' (%s)", typeInfo.getType().getName(), path);
    }

    public ExecutionTypeInfo getTypeInfo() {
        return typeInfo;
    }

    public ExecutionPath getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "NonNullableFieldWasNullException{" +
                " path=" + path +
                " typeInfo=" + typeInfo +
                '}';
    }
}
