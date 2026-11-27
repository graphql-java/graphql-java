package graphql.schema;

import graphql.PublicApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A {@link graphql.schema.SelectedField} represents a field that occurred in a query selection set during
 * execution and they are returned from using the {@link graphql.schema.DataFetchingFieldSelectionSet}
 * interface returned via {@link DataFetchingEnvironment#getSelectionSet()}
 */
@PublicApi
@NullMarked
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
     * @return the singleton list containing this entry's concrete parent object type
     */
    List<GraphQLObjectType> getObjectTypes();

    /**
     * @return the singleton list containing this entry's concrete parent type name
     */
    List<String> getObjectTypeNames();

    /**
     * @return the singleton list containing the concrete field definition
     */
    List<GraphQLFieldDefinition> getFieldDefinitions();

    /**
     * @return the type of this field
     */
    GraphQLOutputType getType();

    /**
     * @return the arguments resolved on demand with this concrete definition and execution variables
     */
    Map<String, Object> getArguments();

    /**
     * @return the level of the selected field within the query
     */
    int getLevel();

    /**
     * @return whether this entry is one of several possible concrete parent types at its level
     */
    boolean isConditional();

    /**
     * @return the alias of the selected field or null if not alias was used
     */
    @Nullable String getAlias();

    /**
     * The result key is either the field query alias OR the field name in that preference order
     *
     * @return the result key of the selected field
     */
    String getResultKey();

    /**
     * Returns the parent within this lookahead view. Immediate fields in the data fetcher's
     * view have no parent; descendants refer to their parent selected field.
     *
     * @return the fields selected parent or null if there is not one
     */
    @Nullable SelectedField getParentField();

    /**
     * @return a sub selection set (if it has any)
     */
    DataFetchingFieldSelectionSet getSelectionSet();
}
