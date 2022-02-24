package graphql.schema;

import graphql.PublicApi;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import graphql.util.TreeTransformerUtil;

/**
 * GraphQLTypeVisitor can be used to visit all the elements of a schema
 * (types, fields, directives and so on) in a visitor pattern.
 *
 * @see GraphQLTypeVisitorStub
 */
@PublicApi
public interface GraphQLTypeVisitor {
    TraversalControl visitGraphQLArgument(GraphQLArgument node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLInterfaceType(GraphQLInterfaceType node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLEnumType(GraphQLEnumType node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLEnumValueDefinition(GraphQLEnumValueDefinition node, TraverserContext<GraphQLSchemaElement> context);

    TraversalControl visitGraphQLFieldDefinition(GraphQLFieldDefinition node, TraverserContext<GraphQLSchemaElement> context);

    /**
     * This method will be called twice.  Once for a directive definition in a schema and then do each time a directive is applied to a schema element
     *
     * When it's applied to a schema element then {@link TraverserContext#getParentNode()} will be the schema element that this is applied to.
     *
     * The graphql-java code base is trying to slowly move away from using {@link GraphQLDirective}s when they really should be {@link GraphQLAppliedDirective}s
     * and this is another place that has been left in.  In the future this behavior will change and this will only visit directive definitions of a schema, not where
     * they are applied.
     *
     * @param node the directive
     * @param context the traversal context
     * @return how to control the visitation processing
     */
    TraversalControl visitGraphQLDirective(GraphQLDirective node, TraverserContext<GraphQLSchemaElement> context);

    /**
     * This method will be called when a directive is applied to a schema element.
     *
     * The {@link TraverserContext#getParentNode()} will be the schema element that this is applied to.
     *
     * The graphql-java code base is trying to slowly move away from using {@link GraphQLDirective}s when they really should be {@link GraphQLAppliedDirective}s
     *
     * @param node the applied directive
     * @param context the traversal context
     * @return how to control the visitation processing
     */
    default TraversalControl visitGraphQLAppliedDirective(GraphQLAppliedDirective node, TraverserContext<GraphQLSchemaElement> context) {
        return TraversalControl.CONTINUE;
    }

    default TraversalControl visitGraphQLAppliedDirectiveArgument(GraphQLAppliedDirectiveArgument node, TraverserContext<GraphQLSchemaElement> context) {
        return TraversalControl.CONTINUE;
    }

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

    /**
     * This helper method can be used to "change" a node when returning control from this visitor
     *
     * @param context        the current traversal context
     * @param newChangedNode the new to be changed at this place
     *
     * @return this will always sent back TraversalControl.CONTINUE
     */
    default TraversalControl changeNode(TraverserContext<GraphQLSchemaElement> context, GraphQLSchemaElement newChangedNode) {
        return TreeTransformerUtil.changeNode(context, newChangedNode);
    }

    /**
     * This helper method can be used to "delete" the current node when returning control from this visitor
     *
     * @param context the current traversal context which is pointing to the current node to be deleted
     *
     * @return this will always sent back TraversalControl.CONTINUE
     */
    default TraversalControl deleteNode(TraverserContext<GraphQLSchemaElement> context) {
        return TreeTransformerUtil.deleteNode(context);
    }

    /**
     * This helper method can be used to "insert a new node" AFTER the current node when returning control from this visitor
     *
     * @param context       the current traversal context
     * @param toInsertAfter the new to be inserted AFTER this current code
     *
     * @return this will always sent back TraversalControl.CONTINUE
     */
    default TraversalControl insertAfter(TraverserContext<GraphQLSchemaElement> context, GraphQLSchemaElement toInsertAfter) {
        return TreeTransformerUtil.insertAfter(context, toInsertAfter);
    }

    /**
     * This helper method can be used to "insert a new node" BEFORE the current node when returning control from this visitor
     *
     * @param context        the current traversal context
     * @param toInsertBefore the new to be inserted BEFORE this current code
     *
     * @return this will always sent back TraversalControl.CONTINUE
     */
    default TraversalControl insertBefore(TraverserContext<GraphQLSchemaElement> context, GraphQLSchemaElement toInsertBefore) {
        return TreeTransformerUtil.insertBefore(context, toInsertBefore);
    }

}
