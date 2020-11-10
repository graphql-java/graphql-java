package graphql.schema;

import graphql.PublicApi;

import java.util.Map;

/**
 * A {@link graphql.schema.SelectedField} represents a field that occurred in a query selection set during
 * execution and they are returned from using the {@link graphql.schema.DataFetchingFieldSelectionSet}
 * interface returned via {@link DataFetchingEnvironment#getSelectionSet()}
 */
@PublicApi
public interface SelectedField {
    /**
     * @return the simple name of the selected field
     */
    String getName();

    /**
     * The selected field has a simple qualified name which is the path of field names to it.
     * For example `payments/amount`.
     *
     * @return the simple qualified name of the selected field
     */
    String getQualifiedName();

    /**
     * The selected field has a more complex type qualified name which is the path of field names to it
     * as well as the object type of the parent.  For example `Invoice.payments/Payment.amount`
     *
     * @return the fully qualified name of the selected field
     */
    String getFullyQualifiedName();

    /**
     * @return the containing object type of this selected field
     */
    GraphQLObjectType getObjectType();

    /**
     * @return the field runtime definition
     */
    GraphQLFieldDefinition getFieldDefinition();

    /**
     * @return a map of the arguments to this selected field
     */
    Map<String, Object> getArguments();

    /**
     * @return the level of the selected field within the query
     */
    int getLevel();

    /**
     * @return whether the field is conditionally present.
     */
    boolean isConditional();

    /**
     * @return the alias of the selected field or null if not alias was used
     */
    String getAlias();

    /**
     * The result key is either the field query alias OR the field name in that preference order
     *
     * @return the result key of the selected field
     */
    String getResultKey();

    /**
     * This will return the parent of the selected field OR null if there is no single parent, it that field
     * was a top level field OR the parent was a non concrete field.
     *
     * @return the fields selected parent or null if there is not one
     */
    SelectedField getParentField();

    /**
     * @return a sub selection set (if it has any)
     */
    DataFetchingFieldSelectionSet getSelectionSet();
}
