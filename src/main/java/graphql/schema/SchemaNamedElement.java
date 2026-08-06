package graphql.schema;

import graphql.ExperimentalApi;
import graphql.language.Node;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A named element in a GraphQL schema.
 */
@ExperimentalApi
@NullMarked
public interface SchemaNamedElement {

    /**
     * Returns the name of this schema element.
     *
     * @return the name of this schema element
     */
    String getName();

    /**
     * Returns the description of this schema element.
     *
     * @return the description of this schema element, or {@code null} when absent
     */
    @Nullable String getDescription();

    /**
     * Returns the source definition from which this schema element was created.
     *
     * @return the source definition of this schema element, or {@code null} when unavailable
     */
    @Nullable Node<?> getDefinition();
}
