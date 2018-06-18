package graphql.schema;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import static graphql.util.TraversalControl.CONTINUE;

/**
 * Base implementation of {@link GraphqlTypeVisitor} for convenience.
 * Overwrite only required methods and/or {@link #visitGraphQLType(GraphQLType, TraverserContext)} as default fallback.
 */
@PublicApi
public class GraphqlTypeVisitorStub implements GraphqlTypeVisitor {
    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLList(GraphQLList node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLTypeReference(GraphQLTypeReference node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLType> context) {
        return visitGraphQLType(node,context);
    }

    protected TraversalControl visitGraphQLType(GraphQLType node, TraverserContext<GraphQLType> context) {
        return CONTINUE;
    }
}
