package graphql.execution;

import static graphql.Assert.assertNotNull;

/**
 * See (http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability), but if a non nullable field
 * actually resolves to a null value and the parent type is nullable then the parent must in fact become null
 * so we use exceptions to indicate this special case
 */
public class NonNullableFieldWasNullException extends RuntimeException {

    private final ExecutionInfo executionInfo;
    private final ExecutionPath path;


    public NonNullableFieldWasNullException(ExecutionInfo executionInfo, ExecutionPath path) {
        super(
                mkMessage(assertNotNull(executionInfo),
                        assertNotNull(path))
        );
        this.executionInfo = executionInfo;
        this.path = path;
    }

    public NonNullableFieldWasNullException(NonNullableFieldWasNullException previousException) {
        super(
                mkMessage(
                        assertNotNull(previousException.executionInfo.getParent()),
                        assertNotNull(previousException.executionInfo.getParent().getPath())
                ),
                previousException
        );
        this.executionInfo = previousException.executionInfo.getParent();
        this.path = previousException.executionInfo.getParent().getPath();
    }


    private static String mkMessage(ExecutionInfo executionInfo, ExecutionPath path) {
        if (executionInfo.hasParentType()) {
            return String.format("Cannot return null for non-nullable type: '%s' within parent '%s' (%s)", executionInfo.getType().getName(), executionInfo.getParent().getType().getName(), path);
        }
        return String.format("Cannot return null for non-nullable type: '%s' (%s)", executionInfo.getType().getName(), path);
    }

    public ExecutionInfo getExecutionInfo() {
        return executionInfo;
    }

    public ExecutionPath getPath() {
        return path;
    }

    @Override
    public String toString() {
        return "NonNullableFieldWasNullException{" +
                " path=" + path +
                " executionInfo=" + executionInfo +
                '}';
    }
}
