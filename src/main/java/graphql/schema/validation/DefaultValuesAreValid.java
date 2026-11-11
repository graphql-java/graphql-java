package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.InputValueWithState;
import org.jspecify.annotations.NullMarked;

import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static java.lang.String.format;

/**
 * Schema validation rule ensuring argument and input field default values are valid for their types.
 */
@Internal
@NullMarked
public class DefaultValuesAreValid implements SchemaValidationRule {

    private final InputValueValidator inputValueValidator;
    private final SchemaValidationErrorCollector errors;

    public DefaultValuesAreValid(InputValueValidator inputValueValidator, SchemaValidationErrorCollector errors) {
        this.inputValueValidator = inputValueValidator;
        this.errors = errors;
    }

    @Override
    public void checkInputObjectField(GraphQLInputObjectType inputObjectType, GraphQLInputObjectField inputObjectField) {
        if (inputObjectField.hasSetDefaultValue()) {
            checkDefaultValue(inputObjectField.getInputFieldDefaultValue(), inputObjectField.getType());
        }
    }

    @Override
    public void checkArgument(GraphQLSchemaElement fieldOrDirective, GraphQLArgument argument) {
        if (argument.hasSetDefaultValue()) {
            checkDefaultValue(argument.getArgumentDefaultValue(), argument.getType());
        }
    }

    private void checkDefaultValue(InputValueWithState defaultValue, GraphQLInputType type) {
        if (inputValueValidator.isValidValue(defaultValue, type)) {
            return;
        }
        errors.addError(new SchemaValidationError(SchemaValidationErrorType.InvalidDefaultValue,
                format("Invalid default value %s for type %s", defaultValue.getValue(), simplePrint(type))));
    }
}
