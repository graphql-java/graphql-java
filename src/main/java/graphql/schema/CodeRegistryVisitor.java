package graphql.schema;

import graphql.Internal;
import graphql.introspection.Introspection;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import static graphql.Assert.assertTrue;
import static graphql.schema.FieldCoordinates.coordinates;
import static graphql.util.TraversalControl.CONTINUE;

/**
 * This ensure that all fields have data fetchers and that unions and interfaces have type resolvers
 */
@Internal
public class CodeRegistryVisitor extends GraphQLTypeVisitorStub {
    private final GraphQLCodeRegistry.Builder codeRegistry;

    public CodeRegistryVisitor(GraphQLCodeRegistry.Builder codeRegistry) {
        this.codeRegistry = codeRegistry;
        Introspection.addCodeForIntrospectionTypes(codeRegistry);
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context) {
        assertTrue(codeRegistry.getTypeResolver(node) != null,
                () -> String.format("You MUST provide a type resolver for the interface type '%s'", node.getName()));
        return CONTINUE;
    }

    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLSchemaElement> context) {
        assertTrue(codeRegistry.getTypeResolver(node) != null,
                () -> String.format("You MUST provide a type resolver for the union type '%s'", node.getName()));
        return CONTINUE;
    }
}
