package graphql.schema.validation;

import graphql.Internal;
import graphql.schema.GraphQLAppliedDirective;
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
        GraphQLAppliedDirective deprecatedDirective = inputObjectField.getAppliedDirective("deprecated");

        if (deprecatedDirective != null && GraphQLTypeUtil.isNonNull(inputObjectField.getType()) && !inputObjectField.hasSetDefaultValue()) {
            GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) context.getParentNode();
            SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
            String message = format("Required input field %s.%s cannot be deprecated.", inputObjectType.getName(), inputObjectField.getName());
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.RequiredInputFieldCannotBeDeprecated, message));
        }
        return TraversalControl.CONTINUE;
    }

}
