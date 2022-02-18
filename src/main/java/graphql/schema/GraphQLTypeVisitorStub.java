package graphql.schema;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

import static graphql.util.TraversalControl.CONTINUE;

/**
 * Base implementation of {@link GraphQLTypeVisitor} for convenience.
 * Overwrite only required methods and/or {@link #visitGraphQLType(GraphQLSchemaElement, TraverserContext)} as default fallback.
 */
@PublicApi
public class GraphQLTypeVisitorStub implements GraphQLTypeVisitor {

    @Override
    public TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLAppliedDirective(GraphQLAppliedDirective node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLDirective(GraphQLDirective node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLList(GraphQLList node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLTypeReference(GraphQLTypeReference node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    @Override
    public TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLSchemaElement> context) {
        return visitGraphQLType(node, context);
    }

    protected TraversalControl visitGraphQLType(GraphQLSchemaElement node, TraverserContext<GraphQLSchemaElement> context) {
        return CONTINUE;
    }
}
