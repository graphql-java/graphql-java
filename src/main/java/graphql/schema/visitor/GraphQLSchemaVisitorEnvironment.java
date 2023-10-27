package graphql.schema.visitor;

import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;

import java.util.List;

public interface GraphQLSchemaVisitorEnvironment<T extends GraphQLSchemaElement> {

    /**
     * @return the element that is being visited
     */
    T getElement();

    /**
     * This returns the schema element that led to this element, eg a field is contained
     * in a type which is pointed to be another field say.
     *
     * @return a list of schema elements leading to this current element
     */
    List<GraphQLSchemaElement> getLeadingElements();

    /**
     * This returns the schema element that led to this element but with {@link graphql.schema.GraphQLModifiedType} wrappers
     * removed.
     *
     * @return a list of schema elements leading to this current element
     */
    List<GraphQLSchemaElement> getUnwrappedLeadingElements();

    /**
     * @return the schema that is being visited upon
     */
    GraphQLSchema getSchema();

    /**
     * This will return a value if the visitation call was via {@link graphql.schema.SchemaTransformer}
     *
     * @return a code registry builder
     */
    GraphQLCodeRegistry.Builder getCodeRegistry();


    /**
     * @return When returned the traversal will continue as planned.
     */
    GraphQLSchemaTraversalControl ok();

    /**
     * @return When returned from a {@link GraphQLSchemaVisitor}'s method, indicates exiting the traversal.
     */
    GraphQLSchemaTraversalControl quit();

    /**
     * Called to change the current node to the specific node
     *
     * @param schemaElement the schema element to change
     *
     * @return a control that changes the current node to a the given node
     */
    GraphQLSchemaTraversalControl changeNode(T schemaElement);

    /**
     * Called to delete the current node
     *
     * @return a control that deletes the current node
     */
    GraphQLSchemaTraversalControl deleteNode();

    /**
     * Called to insert the current schema element after the specified schema element
     *
     * @param toInsertAfter the schema element to after before
     *
     * @return a control that inserts the given node after the current node
     */
    GraphQLSchemaTraversalControl insertAfter(T toInsertAfter);

    /**
     * Called to insert the current schema element before the specified schema element
     *
     * @param toInsertBefore the schema element to insert before
     *
     * @return a control that inserts the given node before the current node
     */
    GraphQLSchemaTraversalControl insertBefore(T toInsertBefore);

}
