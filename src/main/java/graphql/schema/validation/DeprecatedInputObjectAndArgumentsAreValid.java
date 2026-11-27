package graphql.schema.validation;

import graphql.Directives;
import graphql.Internal;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLSchemaElement;
import org.jspecify.annotations.NullMarked;

import static graphql.schema.GraphQLTypeUtil.isNonNull;
import static java.lang.String.format;

/**
 * Spec: the @deprecated directive must not appear on required (non-null without a default) arguments
 * or input object field definitions.
 */
@Internal
@NullMarked
public class DeprecatedInputObjectAndArgumentsAreValid implements SchemaValidationRule {

    private static final String DEPRECATED_DIRECTIVE_NAME = Directives.DEPRECATED_DIRECTIVE_DEFINITION.getName();

    private final SchemaValidationErrorCollector errors;

    public DeprecatedInputObjectAndArgumentsAreValid(SchemaValidationErrorCollector errors) {
        this.errors = errors;
    }

    @Override
    public void checkInputObjectField(GraphQLInputObjectType type, GraphQLInputObjectField inputObjectField) {
        if (!isNonNull(inputObjectField.getType()) || inputObjectField.hasSetDefaultValue()) {
            return;
        }
        if (inputObjectField.getAppliedDirective(DEPRECATED_DIRECTIVE_NAME) == null) {
            return;
        }
        addError(SchemaValidationErrorType.RequiredInputFieldCannotBeDeprecated,
                format("Required input field '%s.%s' cannot be deprecated.", type.getName(), inputObjectField.getName()));
    }

    @Override
    public void checkArgument(GraphQLSchemaElement fieldOrDirective, GraphQLArgument argument) {
        if (!argument.isDeprecated() || !isNonNull(argument.getType()) || argument.hasSetDefaultValue()) {
            return;
        }
        if (fieldOrDirective instanceof GraphQLFieldDefinition) {
            addError(SchemaValidationErrorType.RequiredFieldArgumentCannotBeDeprecated,
                    format("Required argument '%s' on field '%s' cannot be deprecated.", argument.getName(), ((GraphQLFieldDefinition) fieldOrDirective).getName()));
        } else if (fieldOrDirective instanceof GraphQLDirective) {
            addError(SchemaValidationErrorType.RequiredDirectiveArgumentCannotBeDeprecated,
                    format("Required argument '%s' on directive '%s' cannot be deprecated.", argument.getName(), ((GraphQLDirective) fieldOrDirective).getName()));
        }
    }

    private void addError(SchemaValidationErrorType errorType, String message) {
        errors.addError(new SchemaValidationError(errorType, message));
    }
}
