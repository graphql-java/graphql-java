package graphql.schema.validation;

import graphql.ExperimentalApi;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.schema.GraphQLUnmodifiedType;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import java.util.LinkedHashSet;
import java.util.Set;

import static java.lang.String.format;

/*
 * Spec: If the Input Object is a OneOf Input Object then:
 * The type of the input field must be nullable.
 * The input field must not have a default value.
 */
@ExperimentalApi
public class OneOfInputObjectRules extends GraphQLTypeVisitorStub {

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType inputObjectType, TraverserContext<GraphQLSchemaElement> context) {
        if (!inputObjectType.isOneOf()) {
            return TraversalControl.CONTINUE;
        }
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        if (!canBeProvidedAFiniteValue(inputObjectType, new LinkedHashSet<>())) {
            String message = format("OneOf Input Object %s must be inhabited but all fields recursively reference only other OneOf Input Objects forming an unresolvable cycle.", inputObjectType.getName());
            errorCollector.addError(new SchemaValidationError(SchemaValidationErrorType.OneOfNotInhabited, message));
        }
        return TraversalControl.CONTINUE;
    }

    private boolean canBeProvidedAFiniteValue(GraphQLInputObjectType oneOfInputObject, Set<GraphQLInputObjectType> visited) {
        if (visited.contains(oneOfInputObject)) {
            return false;
        }
        Set<GraphQLInputObjectType> nextVisited = new LinkedHashSet<>(visited);
        nextVisited.add(oneOfInputObject);
        for (GraphQLInputObjectField field : oneOfInputObject.getFieldDefinitions()) {
            GraphQLType fieldType = field.getType();
            if (GraphQLTypeUtil.isList(fieldType)) {
                return true;
            }
            GraphQLUnmodifiedType namedFieldType = GraphQLTypeUtil.unwrapAll(fieldType);
            if (!(namedFieldType instanceof GraphQLInputObjectType)) {
                return true;
            }
            GraphQLInputObjectType inputFieldType = (GraphQLInputObjectType) namedFieldType;
            if (!inputFieldType.isOneOf()) {
                return true;
            }
            if (canBeProvidedAFiniteValue(inputFieldType, nextVisited)) {
                return true;
            }
        }
        return false;
    }

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
