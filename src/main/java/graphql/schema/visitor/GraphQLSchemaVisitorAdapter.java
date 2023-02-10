package graphql.schema.visitor;

import graphql.Internal;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchemaElement;
import graphql.schema.GraphQLTypeVisitorStub;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import static graphql.schema.visitor.GraphQLSchemaVisitor.FieldVisitorEnvironment;
import static graphql.schema.visitor.GraphQLSchemaVisitor.ObjectVisitorEnvironment;

@Internal
class GraphQLSchemaVisitorAdapter extends GraphQLTypeVisitorStub {

    private final GraphQLSchemaVisitor smartTypeVisitor;

    GraphQLSchemaVisitorAdapter(GraphQLSchemaVisitor smartTypeVisitor) {
        this.smartTypeVisitor = smartTypeVisitor;
    }

    /* ------------------------------
     * GraphQLObjectType
     * ------------------------------  */
    static class ObjectEnv extends GraphQLSchemaVisitorEnvironmentImpl implements ObjectVisitorEnvironment {
        public ObjectEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }
    }

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        return smartTypeVisitor.visitGraphQLObjectType(node, new ObjectEnv(context));
    }

    /* ------------------------------
     * GraphQLFieldDefinition
     * ------------------------------  */
    static class FieldEnv extends GraphQLSchemaVisitorEnvironmentImpl implements FieldVisitorEnvironment {

        public FieldEnv(TraverserContext<GraphQLSchemaElement> context) {
            super(context);
        }

        @Override
        public GraphQLFieldsContainer getFieldsContainer() {
            return (GraphQLFieldsContainer) context.getParentNode();
        }
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        return smartTypeVisitor.visitGraphQLFieldDefinition(node, new FieldEnv(context));
    }
}
