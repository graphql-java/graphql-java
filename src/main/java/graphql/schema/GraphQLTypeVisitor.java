package graphql.schema;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;

@PublicApi
public interface GraphQLTypeVisitor {
    TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLDirective(GraphQLDirective node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLInputObjectField(GraphQLInputObjectField node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLInputObjectType(GraphQLInputObjectType node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLList(GraphQLList node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLNonNull(GraphQLNonNull node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLObjectType(GraphQLObjectType node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLScalarType(GraphQLScalarType node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLTypeReference(GraphQLTypeReference node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLUnionType(GraphQLUnionType node, TraverserContext<GraphQLSchemaElement> context);

    /**
     * Called when a node is visited more than once within a context.  {@link graphql.util.TraverserContext#thisNode()} contains
     * the node
     *
     * @param context the traversal context
     *
     * @return by default CONTINUE
     */
    default TraversalControl visitBackRef(TraverserContext<GraphQLSchemaElement> context) {
        return TraversalControl.CONTINUE;
    }

    // Marker interfaces
    default TraversalControl visitGraphQLModifiedType(GraphQLModifiedType node, TraverserContext<GraphQLSchemaElement> context) {
        throw new UnsupportedOperationException();
    }

    default TraversalControl visitGraphQLCompositeType(GraphQLCompositeType node, TraverserContext<GraphQLSchemaElement> context) {
        throw new UnsupportedOperationException();
    }

    default TraversalControl visitGraphQLDirectiveContainer(GraphQLDirectiveContainer node, TraverserContext<GraphQLSchemaElement> context) {
        throw new UnsupportedOperationException();
    }

    default TraversalControl visitGraphQLFieldsContainer(GraphQLFieldsContainer node, TraverserContext<GraphQLSchemaElement> context) {
        throw new UnsupportedOperationException();
    }

    default TraversalControl visitGraphQLInputFieldsContainer(GraphQLInputFieldsContainer node, TraverserContext<GraphQLSchemaElement> context) {
        throw new UnsupportedOperationException();
    }

    default TraversalControl visitGraphQLInputType(GraphQLInputType node, TraverserContext<GraphQLSchemaElement> context) {
        throw new UnsupportedOperationException();
    }

    default TraversalControl visitGraphQLNullableType(GraphQLNullableType node, TraverserContext<GraphQLSchemaElement> context) {
        throw new UnsupportedOperationException();
    }

    default TraversalControl visitGraphQLOutputType(GraphQLOutputType node, TraverserContext<GraphQLSchemaElement> context) {
        throw new UnsupportedOperationException();
    }

    default TraversalControl visitGraphQLUnmodifiedType(GraphQLUnmodifiedType node, TraverserContext<GraphQLSchemaElement> context) {
        throw new UnsupportedOperationException();
    }


}
