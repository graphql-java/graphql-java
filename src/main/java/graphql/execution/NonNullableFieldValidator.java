package graphql.execution;


import com.google.common.collect.ImmutableList;
import graphql.Directives;
import graphql.GraphQLError;
import graphql.Internal;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLFieldDefinition;

import java.util.List;

/**
 * This will check that a value is non-null when the type definition says it must be and, it will throw {@link NonNullableFieldWasNullException}
 * if this is not the case.
 * <p>
 * It also enforces the {@code @semanticNonNull} directive (see <a href="https://specs.apollo.dev/nullability/v0.4/">the Apollo nullability specification</a>):
 * when a position annotated with {@code @semanticNonNull} resolves to null without a matching error, an error is synthesized while leaving the value null.
 *
 * See: https://spec.graphql.org/October2021/#sec-Errors-and-Non-Nullability
 */
@Internal
public class NonNullableFieldValidator {

    private static final List<Integer> DEFAULT_SEMANTIC_NON_NULL_LEVELS = ImmutableList.of(0);

    private final ExecutionContext executionContext;

    public NonNullableFieldValidator(ExecutionContext executionContext) {
        this.executionContext = executionContext;
    }

    /**
     * Called to check that a value is non-null if the type requires it to be non null
     *
     * @param parameters the execution strategy parameters
     * @param result the result to check
     * @param <T>    the type of the result
     *
     * @return the result back
     *
     * @throws NonNullableFieldWasNullException if the value is null but the type requires it to be non null
     */
    public <T> T validate(ExecutionStrategyParameters parameters, T result) throws NonNullableFieldWasNullException {
        if (result == null) {
            ExecutionStepInfo executionStepInfo = parameters.getExecutionStepInfo();
            if (executionStepInfo.isNonNullType()) {
                // see https://spec.graphql.org/October2021/#sec-Errors-and-Non-Nullability
                //
                //    > If the field returns null because of an error which has already been added to the "errors" list in the response,
                //    > the "errors" list must not be further affected. That is, only one error should be added to the errors list per field.
                //
                // We interpret this to cover the null field path only.  So here we use the variant of addError() that checks
                // for the current path already.
                //
                // Other places in the code base use the addError() that does not care about previous errors on that path being there.
                //
                // We will do this until the spec makes this more explicit.
                //
                final ResultPath path = parameters.getPath();

                NonNullableFieldWasNullException nonNullException = new NonNullableFieldWasNullException(executionStepInfo, path);
                final GraphQLError error = new NonNullableFieldWasNullError(nonNullException);
                addError(parameters, error, path);
                if (executionContext.propagateErrorsOnNonNullContractFailure()) {
                    throw nonNullException;
                }
            } else {
                checkSemanticNonNull(parameters, executionStepInfo);
            }
        }
        return result;
    }

    /**
     * The {@code @semanticNonNull} directive marks a position as only null when there is a matching error.  When the
     * position is nullable in the type system but resolves to null, we synthesize an error so the contract is upheld.
     * Unlike a real non-null type the value itself stays null - the null is not propagated to the parent.
     */
    private void checkSemanticNonNull(ExecutionStrategyParameters parameters, ExecutionStepInfo executionStepInfo) {
        if (!Directives.isSemanticNonNullEnabled()) {
            return;
        }
        GraphQLFieldDefinition fieldDefinition = executionStepInfo.getFieldDefinition();
        if (fieldDefinition == null) {
            return;
        }
        GraphQLAppliedDirective directive = fieldDefinition.getAppliedDirective(Directives.SemanticNonNullDirective.getName());
        if (directive == null) {
            return;
        }
        final ResultPath path = parameters.getPath();
        if (!semanticNonNullLevels(directive).contains(listLevel(path))) {
            return;
        }

        GraphQLError error = new SemanticNonNullFieldWasNullError(executionStepInfo, path);
        addError(parameters, error, path);
    }

    /**
     * The semantic non-null level is the number of list dimensions traversed from the field, with 0 being the
     * outermost position.  It is the count of trailing list segments in the path.
     */
    private static int listLevel(ResultPath path) {
        int level = 0;
        ResultPath current = path;
        while (current != null && current.isListSegment()) {
            level++;
            current = current.getParent();
        }
        return level;
    }

    private static List<Integer> semanticNonNullLevels(GraphQLAppliedDirective directive) {
        GraphQLAppliedDirectiveArgument levels = directive.getArgument("levels");
        if (levels != null && levels.getArgumentValue().isSet()) {
            List<Integer> value = levels.getValue();
            if (value != null) {
                return value;
            }
        }
        return DEFAULT_SEMANTIC_NON_NULL_LEVELS;
    }

    private void addError(ExecutionStrategyParameters parameters, GraphQLError error, ResultPath path) {
        if (parameters.getAlternativeCallContext() != null) {
            parameters.getAlternativeCallContext().addError(error);
        } else {
            executionContext.addError(error, path);
        }
    }
}
