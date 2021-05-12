package graphql.schema.validation;

import graphql.schema.GraphQLArgument;
import graphql.schema.GraphQLInputObjectField;
import graphql.schema.GraphQLInputObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

public class ValuesAreValid extends GraphQLTypeVisitorStub {

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        return super.visitGraphQLInputObjectType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField inputObjectField, TraverserContext<GraphQLSchemaElement> context) {
        SchemaValidationErrorCollector errorCollector = context.getVarFromParents(SchemaValidationErrorCollector.class);
        if (!inputObjectField.hasSetDefaultValue()) {
            return TraversalControl.CONTINUE;
        }
//        try {
//            valueToInternalValue(inputObjectField.getInputFieldDefaultValue(), inputObjectField.getDefaultValueState(), inputObjectField.getType())
//        } catch (CoercingParseLiteralException e) {
//
//        } catch (CoercingParseValueException e) {
//
//        }
//

        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
        return super.visitGraphQLArgument(node, context);
    }
}
