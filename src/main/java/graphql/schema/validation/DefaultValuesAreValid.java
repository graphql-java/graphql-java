package graphql.schema.validation;

import graphql.execution.NonNullableValueCoercedAsNullException;
import graphql.execution.ValuesResolver;
import graphql.language.Value;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.InputValueWithState;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.validation.ValidationUtil;

import java.util.Locale;

import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static java.lang.String.format;

public class DefaultValuesAreValid extends GraphQLTypeVisitorStub {

    private ValidationUtil validationUtil = new ValidationUtil();

    private final Locale locale = Locale.getDefault();


    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        return super.visitGraphQLInputObjectType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField inputObjectField, TraverserContext<GraphQLSchemaElement> context) {
        if (!inputObjectField.hasSetDefaultValue()) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        GraphQLSchema schema = context.getVarFromParents(GraphQLSchema.class);
        InputValueWithState defaultValue = inputObjectField.getInputFieldDefaultValue();
        boolean invalid = false;
        if (defaultValue.isLiteral() &&
                !validationUtil.isValidLiteralValue((Value<?>) defaultValue.getValue(), inputObjectField.getType(), schema, locale)) {
            invalid = true;
        } else if (defaultValue.isExternal() &&
                !isValidExternalValue(schema, defaultValue.getValue(), inputObjectField.getType(), locale)) {
            invalid = true;
        }
        if (invalid) {
            String message = format("Invalid default value %s for type %s", defaultValue.getValue(), simplePrint(inputObjectField.getType()));
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.InvalidDefaultValue, message));
        }
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument argument, TraverserContext<GraphQLSchemaElement> context) {
        if (!argument.hasSetDefaultValue()) {
            return TraversalControl.CONTINUE;
        }
        GraphQLSchema schema = context.getVarFromParents(GraphQLSchema.class);
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        InputValueWithState defaultValue = argument.getArgumentDefaultValue();
        boolean invalid = false;
        if (defaultValue.isLiteral() &&
                !validationUtil.isValidLiteralValue((Value<?>) defaultValue.getValue(), argument.getType(), schema, locale)) {
            invalid = true;
        } else if (defaultValue.isExternal() &&
                !isValidExternalValue(schema, defaultValue.getValue(), argument.getType(), locale)) {
            invalid = true;
        }
        if (invalid) {
            String message = format("Invalid default value %s for type %s", defaultValue.getValue(), simplePrint(argument.getType()));
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.InvalidDefaultValue, message));
        }
        return TraversalControl.CONTINUE;
    }

    private boolean isValidExternalValue(GraphQLSchema schema, Object externalValue, GraphQLInputType type, Locale locale) {
        try {
            ValuesResolver.externalValueToInternalValue(schema.getCodeRegistry().getFieldVisibility(), externalValue, type, locale);
            return true;
        } catch (CoercingParseValueException | NonNullableValueCoercedAsNullException e) {
            return false;
        }
    }
}
