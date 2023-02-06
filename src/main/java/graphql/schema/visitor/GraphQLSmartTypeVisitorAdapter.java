package graphql.schema.visitor;

import graphql.Internal;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

@Internal
class GraphQLSmartTypeVisitorAdapter extends GraphQLTypeVisitorStub {

    private final GraphQLSmartTypeVisitor smartTypeVisitor;

    GraphQLSmartTypeVisitorAdapter(GraphQLSmartTypeVisitor smartTypeVisitor) {
        this.smartTypeVisitor = smartTypeVisitor;
    }

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        return smartTypeVisitor.visitGraphQLObjectType(node, new SmartTypeVisitorEnvironmentImpl(context));
    }


    static class FieldDefinitionEnv extends SmartTypeVisitorEnvironmentImpl implements GraphQLSmartTypeVisitor.GraphQLFieldDefinitionVisitorEnvironment {

        public FieldDefinitionEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }

        @Override
        public GraphQLFieldsContainer getFieldsContainer() {
            return (GraphQLFieldsContainer) context.getParentNode();
        }
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        return smartTypeVisitor.visitGraphQLFieldDefinition(node, new FieldDefinitionEnv(context));
    }
}
