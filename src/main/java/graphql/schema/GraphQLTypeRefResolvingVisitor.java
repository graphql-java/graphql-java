package graphql.schema;

import graphql.Internal;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

@Internal
public class GraphQLTypeRefResolvingVisitor extends GraphQLTypeVisitorStub {
    protected final GraphQLType resolvedType;

    public GraphQLTypeRefResolvingVisitor(GraphQLType resolvedType) {
        this.resolvedType = resolvedType;
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLType> context) {
        node.replaceType((GraphQLOutputType) resolvedType);
        return super.visitGraphQLFieldDefinition(node, context);
    }

    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLType> context) {
        node.replaceType((GraphQLInputType) resolvedType);
        return super.visitGraphQLArgument(node, context);
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLType> context) {
        node.replaceType((GraphQLInputType) resolvedType);
        return super.visitGraphQLInputObjectField(node, context);
    }

    @Override
    public TraversalControl visitGraphQLList(GraphQLList node, TraverserContext<GraphQLType> context) {
        node.replaceType(resolvedType);
        return super.visitGraphQLList(node, context);
    }

    @Override
    public TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLType> context) {
        node.replaceType(resolvedType);
        return super.visitGraphQLNonNull(node, context);
    }
}
