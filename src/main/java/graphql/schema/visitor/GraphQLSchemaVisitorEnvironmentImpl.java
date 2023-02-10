package graphql.schema.visitor;

import graphql.Internal;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchemaElement;
import graphql.util.TraverserContext;

@Internal
class GraphQLSchemaVisitorEnvironmentImpl implements GraphQLSchemaVisitorEnvironment {

    protected final TraverserContext<GraphQLSchemaElement> context;

    GraphQLSchemaVisitorEnvironmentImpl(TraverserContext<GraphQLSchemaElement> context) {
        this.context = context;
    }

    @Override
    public GraphQLCodeRegistry.Builder getCodeRegistry() {
        return context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
    }
}
