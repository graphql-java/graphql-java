package graphql.schema;

import graphql.Internal;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import static graphql.Assert.assertTrue;

/**
 * This ensure that all fields have data fetchers and that unions and interfaces hve type resolvers
 */
@Internal
class GraphQLTypeCodeVisitor extends GraphQLTypeVisitorStub {
    private final GraphQLCodeRegistry.Builder codeRegistry;

    GraphQLTypeCodeVisitor(GraphQLCodeRegistry.Builder codeRegistry) {
        this.codeRegistry = codeRegistry;
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLType> context) {
        GraphQLFieldsContainer parentContainerType = (GraphQLFieldsContainer) context.getParentContext().thisNode();
        DataFetcher dataFetcher = node.getDataFetcher();
        if (dataFetcher == null) {
            dataFetcher = new PropertyDataFetcher<>(node.getName());
        }
        codeRegistry.dataFetcherIfAbsent(parentContainerType, node, dataFetcher);
        return super.visitGraphQLFieldDefinition(node, context);
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLType> context) {
        TypeResolver typeResolver = node.getTypeResolver();
        if (typeResolver != null) {
            codeRegistry.typeResolverIfAbsent(node, typeResolver);
        }
        assertTrue(codeRegistry.getTypeResolver(node) != null, "You MUST provide a type resolver for the interface type '" + node.getName() + "'");
        return super.visitGraphQLInterfaceType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLType> context) {
        TypeResolver typeResolver = node.getTypeResolver();
        if (typeResolver != null) {
            codeRegistry.typeResolverIfAbsent(node, typeResolver);
        }
        assertTrue(codeRegistry.getTypeResolver(node) != null, "You MUST provide a type resolver for the union type '" + node.getName() + "'");
        return super.visitGraphQLUnionType(node, context);
    }
}
