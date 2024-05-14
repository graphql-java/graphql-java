package graphql.schema.validation;

import graphql.Directives;
import graphql.Internal;
import graphql.schema.GraphQLAppliedDirective;
import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLDirective;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import static java.lang.String.format;

/*
  From the spec:
  The @deprecated directive must not appear on required (non-null without a default) arguments
  or input object field definitions.
 */
@Internal
public class DeprecatedInputObjectAndArgumentsAreValid extends GraphQLTypeVisitorStub {

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField inputObjectField, TraverserContext<GraphQLSchemaElement> context) {
        // There can only be at most one @deprecated, because it is not a repeatable directive
        GraphQLAppliedDirective deprecatedDirective = inputObjectField.getAppliedDirective(Directives.DEPRECATED_DIRECTIVE_DEFINITION.getName());

        if (deprecatedDirective != null && GraphQLTypeUtil.isNonNull(inputObjectField.getType()) && !inputObjectField.hasSetDefaultValue()) {
            GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) context.getParentNode();
            SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
            String message = format("Required input field '%s.%s' cannot be deprecated.", inputObjectType.getName(), inputObjectField.getName());
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.RequiredInputFieldCannotBeDeprecated, message));
        }
        return TraversalControl.CONTINUE;
    }

    // An argument can appear as either a field argument or a directive argument. This visitor will visit both field arguments and directive arguments.
    // An applied directive's argument cannot be deprecated.
    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument argument, TraverserContext<GraphQLSchemaElement> context) {
        // There can only be at most one @deprecated, because it is not a repeatable directive
        GraphQLAppliedDirective deprecatedDirective = argument.getAppliedDirective(Directives.DEPRECATED_DIRECTIVE_DEFINITION.getName());

        if (deprecatedDirective != null && GraphQLTypeUtil.isNonNull(argument.getType()) && !argument.hasSetDefaultValue()) {
            if (context.getParentNode() instanceof GraphQLFieldDefinition) {
                GraphQLFieldDefinition fieldDefinition = (GraphQLFieldDefinition) context.getParentNode();
                SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
                String message = format("Required argument '%s' on field '%s' cannot be deprecated.", argument.getName(), fieldDefinition.getName());
                errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.RequiredFieldArgumentCannotBeDeprecated, message));
            } else if (context.getParentNode() instanceof GraphQLDirective) {
                GraphQLDirective directive = (GraphQLDirective) context.getParentNode();
                SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
                String message = format("Required argument '%s' on directive '%s' cannot be deprecated.", argument.getName(), directive.getName());
                errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.RequiredDirectiveArgumentCannotBeDeprecated, message));
            }
        }
        return TraversalControl.CONTINUE;
    }

}
