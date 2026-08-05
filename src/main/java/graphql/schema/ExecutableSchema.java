package graphql.schema;

import graphql.ExperimentalApi;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * An executable view of a GraphQL schema.
 *
 * <p>The view owns schema-dependent behavior such as field visibility and scalar coercion, while
 * the {@code Schema*} interfaces expose the type-system shape used by operation processing.</p>
 */
@ExperimentalApi
@NullMarked
public interface ExecutableSchema {

    /**
     * @return the query root
     */
    SchemaObject getQueryType();

    /**
     * @return the mutation root, or {@code null} when absent
     */
    @Nullable SchemaObject getMutationType();

    /**
     * @return the subscription root, or {@code null} when absent
     */
    @Nullable SchemaObject getSubscriptionType();

    /**
     * @param name the type name
     *
     * @return the named type, or {@code null} when absent
     */
    @Nullable SchemaType getType(String name);

    /**
     * @param name the directive name
     *
     * @return the directive definition, or {@code null} when absent
     */
    @Nullable SchemaDirective getDirective(String name);

    /**
     * @return this schema's introspection schema type
     */
    SchemaObject getIntrospectionSchemaType();

    /**
     * Returns a visible field, including GraphQL introspection meta-fields.
     *
     * @param parentType the composite parent
     * @param fieldName the field name
     *
     * @return the field, or {@code null} when absent or invisible
     */
    @Nullable SchemaField getField(SchemaComposite parentType, String fieldName);

    /**
     * @param parentType the field container
     *
     * @return the visible fields declared by the container
     */
    List<? extends SchemaField> getFields(SchemaFieldsContainer parentType);

    /**
     * @param parentType the input object
     * @param fieldName the input field name
     *
     * @return the visible input field, or {@code null} when absent or invisible
     */
    @Nullable SchemaInputField getInputField(
            SchemaInputObject parentType,
            String fieldName);

    /**
     * @param parentType the input object
     *
     * @return the visible fields declared by the input object
     */
    List<? extends SchemaInputField> getInputFields(SchemaInputObject parentType);

    /**
     * @param compositeType an object, interface, or union
     *
     * @return the concrete object types represented by the composite type
     */
    List<? extends SchemaObject> getPossibleTypes(SchemaComposite compositeType);

    /**
     * @param compositeType an object, interface, or union
     * @param objectType the concrete object type
     *
     * @return whether the object is represented by the composite type
     */
    boolean isPossibleType(
            SchemaComposite compositeType,
            SchemaObject objectType);

    /**
     * @param scalarType the scalar
     *
     * @return the schema-specific scalar coercer
     */
    Coercing<?, ?> getScalarCoercing(SchemaScalar scalarType);
}
