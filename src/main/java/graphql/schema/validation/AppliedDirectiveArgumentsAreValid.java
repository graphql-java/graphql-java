package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLInputType;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.NullMarked;

import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static java.lang.String.format;

/**
 * Schema validation rule ensuring the argument values of applied directives are valid for their types.
 */
@Internal
@NullMarked
public class AppliedDirectiveArgumentsAreValid implements SchemaValidationRule {

    private final InputValueValidator inputValueValidator;
    private final SchemaValidationErrorCollector errors;

    public AppliedDirectiveArgumentsAreValid(InputValueValidator inputValueValidator, SchemaValidationErrorCollector errors) {
        this.inputValueValidator = inputValueValidator;
        this.errors = errors;
    }

    @Override
    public void checkDirectiveContainer(GraphQLDirectiveContainer container) {
        for (GraphQLDirective directive : container.getDirectives()) {
            for (GraphQLArgument argument : directive.getArguments()) {
                checkArgument(directive.getName(), argument.getName(), argument.getArgumentValue(), argument.getType());
            }
        }
        for (GraphQLAppliedDirective directive : container.getAppliedDirectives()) {
            for (GraphQLAppliedDirectiveArgument argument : directive.getArguments()) {
                checkArgument(directive.getName(), argument.getName(), argument.getArgumentValue(), argument.getType());
            }
        }
    }

    private void checkArgument(String directiveName, String argumentName, InputValueWithState argumentValue, GraphQLInputType argumentType) {
        boolean invalid = !inputValueValidator.isValidValue(argumentValue, argumentType)
                || (argumentValue.isNotSet() && isNonNull(argumentType));
        if (invalid) {
            errors.addError(new SchemaValidationError(SchemaValidationErrorType.InvalidAppliedDirectiveArgument,
                    format("Invalid argument '%s' for applied directive of name '%s'", argumentName, directiveName)));
        }
    }
}
