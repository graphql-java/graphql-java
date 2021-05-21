package graphql.schema.validation;

import graphql.execution.NonNullableValueCoercedAsNullException;
import graphql.execution.ValuesResolver;
import graphql.language.Value;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.ValueState;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.validation.ValidationUtil;

import static graphql.schema.GraphQLTypeUtil.simplePrint;
import static java.lang.String.format;

public class AppliedDirectiveArgumentsAreValid extends GraphQLTypeVisitorStub {

    private ValidationUtil validationUtil = new ValidationUtil();


    public TraversalControl visitGraphQLArgument(GraphQLArgument argument, TraverserContext<GraphQLSchemaElement> context) {
        // a directive argument is represented as GraphQLArgument.value
        if (!argument.hasSetValue()) {
            return TraversalControl.CONTINUE;
        }
        GraphQLSchema schema = context.getVarFromParents(GraphQLSchema.class);
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        ValueState argumentState = argument.getValueState();
        boolean invalid = false;
        if (argumentState == ValueState.LITERAL &&
                !validationUtil.isValidLiteralValue((Value<?>) argument.getArgumentValue(), argument.getType(), schema)) {
            invalid = true;
        } else if (argumentState == ValueState.EXTERNAL_VALUE &&
                !isValidExternalValue(schema, argument.getArgumentValue(), argument.getType())) {
            invalid = true;
        }
        if (invalid) {
            String message = format("Invalid argument %s for type %s for applied directive", argument.getValueState(), simplePrint(argument.getType()));
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.InvalidAppliedDirectiveArgument, message));
        }
        return TraversalControl.CONTINUE;
    }

    private boolean isValidExternalValue(GraphQLSchema schema, Object externalValue, GraphQLInputType type) {
        try {
            ValuesResolver.externalValueToInternalValue(schema.getCodeRegistry().getFieldVisibility(), externalValue, type);
            return true;
        } catch (CoercingParseValueException | NonNullableValueCoercedAsNullException e) {
            return false;
        }
    }
}
