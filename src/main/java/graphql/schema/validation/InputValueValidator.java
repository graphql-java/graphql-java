package graphql.schema.validation;

import graphql.GraphQLContext;
import graphql.Internal;
import graphql.execution.NonNullableValueCoercedAsNullException;
import graphql.execution.ValuesResolver;
import graphql.language.Value;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.InputValueWithState;
import graphql.validation.ValidationUtil;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Checks that default values and applied directive argument values are valid for their input types.
 */
@Internal
@NullMarked
public class InputValueValidator {

    private final ValidationUtil validationUtil = new ValidationUtil();
    private final GraphQLContext graphQLContext = GraphQLContext.getDefault();
    private final GraphQLSchema schema;
    private final SchemaValidationErrorCollector errors;

    public InputValueValidator(GraphQLSchema schema, SchemaValidationErrorCollector errors) {
        this.schema = schema;
        this.errors = errors;
    }

    public boolean isValidValue(InputValueWithState value, GraphQLInputType type) {
        if (value.isLiteral()) {
            return validationUtil.isValidLiteralValue((Value<?>) value.getValue(), type, schema, graphQLContext, Locale.getDefault());
        }
        // coercing external values expands input field defaults, which never terminates once defaults form a cycle
        if (!value.isExternal() || errors.containsValidationError(SchemaValidationErrorType.DefaultValueCircularRef)) {
            return true;
        }
        return isValidExternalValue(value.getValue(), type);
    }

    private boolean isValidExternalValue(@Nullable Object externalValue, GraphQLInputType type) {
        try {
            ValuesResolver.externalValueToInternalValue(schema.getCodeRegistry().getFieldVisibility(), externalValue, type, graphQLContext, Locale.getDefault());
            return true;
        } catch (CoercingParseValueException | NonNullableValueCoercedAsNullException e) {
            return false;
        }
    }
}
