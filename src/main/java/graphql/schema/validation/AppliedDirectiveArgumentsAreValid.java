package graphql.schema.validation;

import graphql.Internal;
import graphql.execution.NonNullableValueCoercedAsNullException;
import graphql.execution.ValuesResolver;
import graphql.language.Value;
import graphql.schema.CoercingParseValueException;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.InputValueWithState;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.validation.ValidationUtil;

import static java.lang.String.format;

@Internal
public class AppliedDirectiveArgumentsAreValid extends GraphQLTypeVisitorStub {

    private ValidationUtil validationUtil = new ValidationUtil();


    @Override
    public TraversalControl visitGraphQLDirective(GraphQLDirective directive, TraverserContext<GraphQLSchemaElement> context) {
        // if there is no parent it means it is just a directive definition and not an applied directive
        if (context.getParentNode() != null) {
            for (GraphQLArgument graphQLArgument : directive.getArguments()) {
                checkArgument(directive, graphQLArgument, context);
            }
        }
        return TraversalControl.CONTINUE;
    }

    private void checkArgument(GraphQLDirective directive, GraphQLArgument argument, TraverserContext<GraphQLSchemaElement> context) {
        if (!argument.hasSetValue()) {
            return;
        }
        GraphQLSchema schema = context.getVarFromParents(GraphQLSchema.class);
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        InputValueWithState argumentValue = argument.getArgumentValue();
        boolean invalid = false;
        if (argumentValue.isLiteral() &&
                !validationUtil.isValidLiteralValue((Value<?>) argumentValue.getValue(), argument.getType(), schema)) {
            invalid = true;
        } else if (argumentValue.isExternal() &&
                !isValidExternalValue(schema, argumentValue.getValue(), argument.getType())) {
            invalid = true;
        }
        if (invalid) {
            String message = format("Invalid argument '%s' for applied directive of name '%s'", argument.getName(), directive.getName());
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.InvalidAppliedDirectiveArgument, message));
        }
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
