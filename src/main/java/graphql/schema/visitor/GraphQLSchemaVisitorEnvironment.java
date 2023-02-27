package graphql.schema.visitor;

import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLSchemaElement;
import graphql.util.TraversalControl;

public interface GraphQLSchemaVisitorEnvironment<T extends GraphQLSchemaElement> {

    /**
     * @return the element that is being visited
     */
    T getElement();

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
     * A synonym method for {@link TraversalControl#CONTINUE}
     */
    TraversalControl ok();

    /**
     * @return When returned from a {@link GraphQLSchemaVisitor}'s method, indicates exiting the traversal.
     * A synonym method for {@link TraversalControl#QUIT}
     */
    TraversalControl quit();

    /**
     * @return When returned from a {@link GraphQLSchemaVisitor}'s method, indicates skipping traversal of a subtree.
     * A synonym method for {@link TraversalControl#ABORT}
     */
    TraversalControl abort();

    /**
     * Called to change the current node to the specific node
     *
     * @param schemaElement the schema element to change
     *
     * @return This will always be {@link TraversalControl#CONTINUE}
     */
    TraversalControl changeNode(T schemaElement);

    /**
     * Called to delete the current node
     *
     * @return This will always be {@link TraversalControl#CONTINUE}
     */
    TraversalControl deleteNode();

    /**
     * Called to insert the current schema element after the specified schema element
     *
     * @param schemaElement the schema element to after before
     *
     * @return This will always be {@link TraversalControl#CONTINUE}
     */
    TraversalControl insertAfter(T schemaElement);

    /**
     * Called to insert the current schema element before the specified schema element
     *
     * @param schemaElement the schema element to insert before
     *
     * @return This will always be {@link TraversalControl#CONTINUE}
     */
    TraversalControl insertBefore(T schemaElement);

}
