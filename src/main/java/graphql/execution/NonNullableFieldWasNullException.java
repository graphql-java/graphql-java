package graphql.execution;

import graphql.Internal;
import graphql.schema.GraphQLType;

import static graphql.Assert.assertNotNull;
import static graphql.schema.GraphQLTypeUtil.simplePrint;

/**
 * See (http://facebook.github.io/graphql/#sec-Errors-and-Non-Nullability), but if a non nullable field
 * actually resolves to a null value and the parent type is nullable then the parent must in fact become null
 * so we use exceptions to indicate this special case
 */
@Internal
public class NonNullableFieldWasNullException extends RuntimeException {

    private final ExecutionStepInfo executionStepInfo;
    private final ResultPath path;


    public NonNullableFieldWasNullException(ExecutionStepInfo executionStepInfo, ResultPath path) {
        super(
                mkMessage(assertNotNull(executionStepInfo),
                        assertNotNull(path))
        );
        this.executionStepInfo = executionStepInfo;
        this.path = path;
    }

    public NonNullableFieldWasNullException(NonNullableFieldWasNullException previousException) {
        super(
                mkMessage(
                        assertNotNull(previousException.executionStepInfo.getParent()),
                        assertNotNull(previousException.executionStepInfo.getParent().getPath())
                ),
                previousException
        );
        this.executionStepInfo = previousException.executionStepInfo.getParent();
        this.path = previousException.executionStepInfo.getParent().getPath();
    }


    private static String mkMessage(ExecutionStepInfo executionStepInfo, ResultPath path) {
        GraphQLType unwrappedTyped = executionStepInfo.getUnwrappedNonNullType();
        if (executionStepInfo.hasParent()) {
            GraphQLType unwrappedParentType = executionStepInfo.getParent().getUnwrappedNonNullType();
            return String.format(
                    "The field at path '%s' was declared as a non null type, but the code involved in retrieving" +
                            " data has wrongly returned a null value.  The graphql specification requires that the" +
                            " parent field be set to null, or if that is non nullable that it bubble up null to its parent and so on." +
                            " The non-nullable type is '%s' within parent type '%s'",
                    path, simplePrint(unwrappedTyped), simplePrint(unwrappedParentType));
        } else {
            return String.format(
                    "The field at path '%s' was declared as a non null type, but the code involved in retrieving" +
                            " data has wrongly returned a null value.  The graphql specification requires that the" +
                            " parent field be set to null, or if that is non nullable that it bubble up null to its parent and so on." +
                            " The non-nullable type is '%s'",
                    path, simplePrint(unwrappedTyped));
        }
    }

    public ExecutionStepInfo getExecutionStepInfo() {
        return executionStepInfo;
    }

    public ResultPath getPath() {
        return path;
    }

    @Override
    public String toString() {
        return getMessage();
    }
}
