package graphql.schema.validation;

import graphql.ExperimentalApi;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import static java.lang.String.format;

/*
 * Spec: If the Input Object is a OneOf Input Object then:
 * The type of the input field must be nullable.
 * The input field must not have a default value.
 */
@ExperimentalApi
public class OneOfInputObjectRules extends GraphQLTypeVisitorStub {

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField inputObjectField, TraverserContext<GraphQLSchemaElement> context) {
        GraphQLInputObjectType inputObjectType = (GraphQLInputObjectType) context.getParentNode();
        if (!inputObjectType.isOneOf()) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        // error messages take from the reference implementation
        if (inputObjectField.hasSetDefaultValue()) {
            String message = format("OneOf input field %s.%s cannot have a default value.", inputObjectType.getName(), inputObjectField.getName());
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.OneOfDefaultValueOnField, message));
        }

        if (GraphQLTypeUtil.isNonNull(inputObjectField.getType())) {
            String message = format("OneOf input field %s.%s must be nullable.", inputObjectType.getName(), inputObjectField.getName());
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.OneOfNonNullableField, message));
        }
        return TraversalControl.CONTINUE;
    }
}
