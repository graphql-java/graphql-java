package graphql.schema.visitor;

import graphql.Internal;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TreeTransformerUtil;

@Internal
class GraphQLSchemaVisitorEnvironmentImpl<T extends GraphQLSchemaElement> implements GraphQLSchemaVisitorEnvironment<T> {

    protected final TraverserContext<GraphQLSchemaElement> context;

    GraphQLSchemaVisitorEnvironmentImpl(TraverserContext<GraphQLSchemaElement> context) {
        this.context = context;
    }

    @Override
    public GraphQLSchema getSchema() {
        return context.getVarFromParents(GraphQLSchema.class);
    }

    @Override
    public GraphQLCodeRegistry.Builder getCodeRegistry() {
        return context.getVarFromParents(GraphQLCodeRegistry.Builder.class);
    }

    @Override
    public T getElement() {
        //noinspection unchecked
        return (T) context.thisNode();
    }

    @Override
    public TraversalControl ok() {
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl quit() {
        return TraversalControl.QUIT;
    }

    @Override
    public TraversalControl abort() {
        return TraversalControl.ABORT;
    }

    @Override
    public TraversalControl changeNode(T schemaElement) {
        TreeTransformerUtil.changeNode(context, schemaElement);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl deleteNode() {
        TreeTransformerUtil.deleteNode(context);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl insertAfter(T schemaElement) {
        TreeTransformerUtil.insertAfter(context, schemaElement);
        return TraversalControl.CONTINUE;
    }

    @Override
    public TraversalControl insertBefore(T schemaElement) {
        TreeTransformerUtil.insertBefore(context, schemaElement);
        return TraversalControl.CONTINUE;
    }
}
